import argparse
import os
import json
import pandas as pd
from multiprocessing import Pool, Manager
import numpy as np

# Define the root directory
root_dir = 'samples'
waveform_file = 'vns_responses.csv'
input_waveform_file = 'vns_stimulations.csv'
dataset_file = 'vns_cervical_rat_data.csv'

# Sample, sim, and nsim indices to be processed
sample_idx = [10, 11, 12, 13, 14]
sim_idx = [7, 8]
nsim_idx = range(80)
myel_sim = 7
unmyel_sim = 8
model = 2

# Function to calculate power of a waveform
def calculate_power(waveform, dt):
    return (waveform ** 2).sum() * dt / len(waveform)

# Function to process a single combination of sample, sim, and nsim
def process_sample_sim_nsim(args):
    sample_id, nsim = args
    data = []
    responses = []
    m_responses = []
    u_responses = []
    stimulations = []
    t_response = None
    
    nsim_path_m = os.path.join(root_dir, str(sample_id), 'models', str(model), 'sims', str(myel_sim), 'n_sims', str(nsim))
    nsim_path_u = os.path.join(root_dir, str(sample_id), 'models', str(model), 'sims', str(unmyel_sim), 'n_sims', str(nsim))
    # Read the JSON file for pulse width and frequency
    sim_file = os.path.join(nsim_path_m, f'{nsim}.json')
    if not os.path.isfile(sim_file):
        return None

    with open(sim_file, 'r') as f:
        sim_data = json.load(f)

    fibers_path_m = os.path.join(root_dir, str(sample_id), 'models', str(model), 'sims', str(myel_sim), 'fibersets', '0', 'diams.txt')
    with open(fibers_path_m, 'r') as f:
        myel_diams = f.readlines()
    fibers_path_u = os.path.join(root_dir, str(sample_id), 'models', str(model), 'sims', str(unmyel_sim), 'fibersets', '0', 'diams.txt')
    with open(fibers_path_u, 'r') as f:
        unmyel_diams = f.readlines()
    
    pulse_width = sim_data['waveform']['BIPHASIC_PULSE_TRAIN']['pulse_width']
    inter_phase = sim_data['waveform']['BIPHASIC_PULSE_TRAIN']['inter_phase']
    pulse_freq = sim_data['waveform']['BIPHASIC_PULSE_TRAIN']['pulse_repetition_freq']
    unit = sim_data['waveform']['global']['unit']
    dt = sim_data['waveform']['global']['dt']

    amplitudes = sim_data['protocol']['amplitudes']
    n_amplitudes = len(amplitudes)
    n_myelinated = len(myel_diams)
    n_unmyelinated = len(unmyel_diams)

    # Read the unscaled waveform from file and scale it by the amplitude
    waveform_file_path = os.path.join(nsim_path_m, 'data', 'inputs', 'waveform.dat')
    if not os.path.isfile(waveform_file_path):
        return None
    
    unscaled_stim = pd.read_csv(waveform_file_path, skiprows=2, header=None).iloc[:, 0].values   
    
    # Read the activation and SFAP files
    outputs_m = os.path.join(nsim_path_m, 'data', 'outputs')
    outputs_u = os.path.join(nsim_path_u, 'data', 'outputs')

    if not os.path.isdir(outputs_m) or not os.path.isdir(outputs_u):
        return None
    
    # if output folders do not have any files return none
    if not os.listdir(outputs_m) or not os.listdir(outputs_u):
        return None

    folder_by_type = {
        "myelinated": outputs_m,
        "unmyelinated": outputs_u
    }

    n_fibers_by_type = {
        "myelinated": n_myelinated,
        "unmyelinated": n_unmyelinated
    }

    for a in range(n_amplitudes):
        entry = {
            'sample_id': sample_id,
            'n_sim': nsim,
            'pulse_width': pulse_width,
            'inter_phase': inter_phase,
            'unit': unit,
            'dt': dt,
            'frequency': pulse_freq
        }
        
        data_by_type = {}

        scaled_waveform = unscaled_stim * amplitudes[a]
        stimulation_power = calculate_power(scaled_waveform * 0.001, dt)
        t_stimulation = np.array(list(range(len(scaled_waveform)))) * dt

        for ftype in ["myelinated", "unmyelinated"]:
            total_ap = 0
            responses_per_fiber = []
            
            fibers_activated = 0
            for f in range(n_fibers_by_type[ftype]):
                activation_file = os.path.join(folder_by_type[ftype], f'activation_inner0_fiber{f}_amp{a}.dat')
                sfap_file = os.path.join(folder_by_type[ftype], f'SFAP_time_inner0_fiber{f}_amp{a}.dat')
                
                if os.path.isfile(activation_file):
                    with open(activation_file, 'r') as af:
                        ap = int(af.read().strip())
                        total_ap += ap
                        if ap > 0:
                            fibers_activated += 1
                
                if os.path.isfile(sfap_file):
                    sfap_df = pd.read_csv(sfap_file, delimiter='\s+', skiprows=1, header=None)
                    responses_per_fiber.append(sfap_df)
        
            # Aggregate waveforms across all fibers
            if responses_per_fiber:
                concatenated_waveform = pd.concat(responses_per_fiber)
                summed_waveform = concatenated_waveform.groupby(concatenated_waveform.columns[0]).sum().reset_index()
                if t_response is None:
                    t_response = summed_waveform.iloc[:, 0].values
                nsim_response = summed_waveform.iloc[:, 1].values
                response_power = calculate_power(pd.Series(nsim_response)*0.000001, dt)
            else:
                response_power = None
                nsim_response = None
            
            data_by_type[ftype] = {
                'fibers_activated': fibers_activated,
                'total_aps': total_ap,
                'v_response': nsim_response,
                'response_power': response_power,
            }

        if data_by_type['myelinated']['v_response'] is None or data_by_type['unmyelinated']['v_response'] is None:
            continue
        overall_response = data_by_type['myelinated']['v_response'] + data_by_type['unmyelinated']['v_response'] 
        overall_power = data_by_type['myelinated']['response_power'] + data_by_type['unmyelinated']['response_power'] 
        # Compile the data 
        entry['amplitude'] = amplitudes[a]
        entry['response_power'] = overall_power
        entry['stimulation_power'] = stimulation_power
        entry['AB_fibers_activated'] = data_by_type['myelinated']['fibers_activated']
        entry['AB_action_potentials'] = data_by_type['myelinated']['total_aps']
        entry['AB_fibers_activation'] = data_by_type['myelinated']['fibers_activated']/n_myelinated
        entry['C_fibers_activated'] = data_by_type['unmyelinated']['fibers_activated']
        entry['C_action_potentials'] = data_by_type['unmyelinated']['total_aps']
        entry['C_fibers_activation'] = data_by_type['unmyelinated']['fibers_activated']/n_unmyelinated

        data.append(entry)

        m_responses.append(data_by_type['myelinated']['v_response'])
        u_responses.append(data_by_type['unmyelinated']['v_response'])
        responses.append(overall_response)
        stimulations.append(scaled_waveform)

    return data, responses, m_responses, u_responses, stimulations, t_response, t_stimulation

# Function to merge results from multiple processes
def merge_results(results):
    data = []
    waveform_data = []
    myel_waveform_data = []
    unmyel_waveform_data = []
    input_waveform_data = []
    out_time_data = None
    in_time_data = None

    waveform_id = 0
    for result in results:
        if result is None:
            continue
        entries, waveforms, m_waveforms, u_waveforms, input_waveforms, out_times, in_times = result
        for e in entries:
            e['waveforms_index'] = waveform_id
            waveform_id += 1

        data.extend(entries)
        waveform_data.extend(waveforms)
        myel_waveform_data.extend(m_waveforms)
        unmyel_waveform_data.extend(u_waveforms)
        input_waveform_data.extend(input_waveforms)
        if out_time_data is None and out_times is not None:
            out_time_data = out_times
        if in_time_data is None and in_times is not None:
            in_time_data = in_times

    return data, waveform_data, myel_waveform_data, unmyel_waveform_data, input_waveform_data, out_time_data, in_time_data

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Process waveform data in parallel.")
    parser.add_argument('--cores', type=int, default=10, help='Number of cores to use')
    args = parser.parse_args()

    num_cores = args.cores

    # Create a list of arguments for each combination of sample, sim, and nsim
    args_list = [(sample_id, nsim) for sample_id in sample_idx for nsim in nsim_idx]

    # Use a multiprocessing Pool to process the samples in parallel
    with Pool(processes=num_cores) as pool:
        results = pool.map(process_sample_sim_nsim, args_list)

    # Merge the results from all processes
    data, waveform_data, myel_waveform_data, unmyel_waveform_data, input_waveform_data, out_time_data, in_time_data = merge_results(results)

    # Convert the data into a DataFrame
    df = pd.DataFrame(data)

    # Combine all waveforms into a single DataFrame
    if waveform_data:
        waveform_df = pd.DataFrame(waveform_data).T
        waveform_df.insert(0, 'time', out_time_data)
        waveform_df.to_csv(waveform_file, index=False)

    # Combine all input waveforms into a single DataFrame
    if input_waveform_data:
        input_waveform_df = pd.DataFrame(input_waveform_data).T
        input_waveform_df.insert(0, 'time', in_time_data)
        input_waveform_df.to_csv(input_waveform_file, index=False)

    if myel_waveform_data:
        myel_waveform_df = pd.DataFrame(myel_waveform_data).T
        myel_waveform_df.insert(0, 'time', out_time_data)
        myel_waveform_df.to_csv('vns_myelinated_responses.csv', index=False)
    
    if unmyel_waveform_data:
        unmyel_waveform_df = pd.DataFrame(unmyel_waveform_data).T
        unmyel_waveform_df.insert(0, 'time', out_time_data)
        unmyel_waveform_df.to_csv('vns_unmyelinated_responses.csv', index=False)    

    # Save the DataFrame to a CSV file
    df.to_csv(dataset_file, index=False)

    print(
f"""Dataset compilation complete. Data saved to '{dataset_file}'.
Waveform data saved to '{waveform_file}'.
Input waveforms saved to '{input_waveform_file}'."""
    )
