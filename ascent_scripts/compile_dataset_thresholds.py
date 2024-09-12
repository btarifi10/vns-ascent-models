import argparse
import os
import json
import pandas as pd
from multiprocessing import Pool, Manager
import numpy as np

# Define the root directory
root_dir = 'samples'
waveform_file = 'vns_thresholds.csv'

# Sample, sim, and nsim indices to be processed
sample_idx = range(10, 13)
myelinated_sim = 5
unmyelinated_sim = 6
nsim_idx = range(80)
model = 2

# Function to process a single combination of sample, sim, and nsim
def process_sample_sim_nsim(args):
    sample_id, nsim = args

    sample_data = os.path.join(root_dir, str(sample_id), 'sample.json')
    if not os.path.isfile(sample_data):
        return None
    with open(sample_data, 'r') as f:
        sample_data = json.load(f)

    sample_nerve_area = sample_data['Morphology']['Nerve']['area']
    sample_nerve_equivalent_diameter = 2 * np.sqrt(sample_nerve_area / np.pi)

    input_sample_file = os.path.join('input', sample_data['sample'], 'mock.json')
    if not os.path.isfile(input_sample_file):
        return None
    with open(input_sample_file, 'r') as f:
        input_sample_data = json.load(f)
    
    sample_nerve_a = input_sample_data['nerve']['a']
    sample_nerve_b = input_sample_data['nerve']['b']

    nsim_path_m = os.path.join(root_dir, str(sample_id), 'models', str(model), 'sims', str(myelinated_sim), 'n_sims', str(nsim))
    nsim_path_u = os.path.join(root_dir, str(sample_id), 'models', str(model), 'sims', str(unmyelinated_sim), 'n_sims', str(nsim))
    # Read the JSON file for pulse width and frequency
    sim_file = os.path.join(nsim_path_m, f'{nsim}.json')
    if not os.path.isfile(sim_file):
        return None
    with open(sim_file, 'r') as f:
        sim_data = json.load(f)

    fibers_path_m = os.path.join(root_dir, str(sample_id), 'models', str(model), 'sims', str(myelinated_sim), 'fibersets', '0', 'diams.txt')
    with open(fibers_path_m, 'r') as f:
        myel_diams = f.readlines()
    fibers_path_u = os.path.join(root_dir, str(sample_id), 'models', str(model), 'sims', str(unmyelinated_sim), 'fibersets', '0', 'diams.txt')
    with open(fibers_path_u, 'r') as f:
        unmyel_diams = f.readlines()
    
    pulse_width = sim_data['waveform']['BIPHASIC_PULSE_TRAIN']['pulse_width']
    pulse_freq = sim_data['waveform']['BIPHASIC_PULSE_TRAIN']['pulse_repetition_freq']

    # Read the activation and SFAP files
    outputs_m = os.path.join(nsim_path_m, 'data', 'outputs')
    outputs_u = os.path.join(nsim_path_u, 'data', 'outputs')

    n_fibers_by_type = {
        "myelinated": len(myel_diams),
        "unmyelinated": len(unmyel_diams)
    }
        
    unmyel_threshes = []
    myel_threshes = []
        
    for f in range(n_fibers_by_type['myelinated']):
        thresh_file = os.path.join(outputs_m, f'thresh_inner0_fiber{f}.dat')
        
        if os.path.isfile(thresh_file):
            with open(thresh_file, 'r') as af:
                thresh = float(af.read().strip())
                myel_threshes.append(np.abs(thresh))
    
    for f in range(n_fibers_by_type['unmyelinated']):
        thresh_file = os.path.join(outputs_u, f'thresh_inner0_fiber{f}.dat')
        
        if os.path.isfile(thresh_file):
            with open(thresh_file, 'r') as af:
                thresh = float(af.read().strip())
                unmyel_threshes.append(np.abs(thresh))

    # Sort thresholds to find the activation levels
    myel_threshes.sort()
    unmyel_threshes.sort()

    # Create a dataset to hold the amplitude and activation levels
    dataset = []

    # Get all unique threshold values from both myelinated and unmyelinated fibers
    unique_amplitudes = sorted(set(myel_threshes + unmyel_threshes))

    # Calculate the activation levels at each unique amplitude
    for amp in unique_amplitudes:
        AB_activation_level = sum(thresh <= amp for thresh in myel_threshes) / len(myel_threshes)
        C_activation_level = sum(thresh <= amp for thresh in unmyel_threshes) / len(unmyel_threshes)
        
        entry = {
            'sample_id': sample_id,
            'nerve_area': np.round(sample_nerve_area, 4),
            'nerve_equivalent_diameter': np.round(sample_nerve_equivalent_diameter, 4),
            'nerve_a': np.round(sample_nerve_a, 4),
            'nerve_b': np.round(sample_nerve_b, 4),
            'n_sim': nsim,
            'pulse_width': pulse_width,
            'frequency': pulse_freq,
            'amplitude': amp,
            'AB_fibers': len(myel_threshes),
            'AB_activation_level': np.round(AB_activation_level, 4),
            'C_fibers': len(unmyel_threshes),
            'C_activation_level': np.round(C_activation_level, 4),
        }
        
        dataset.append(entry)

    return dataset

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Process waveform data in parallel.")
    parser.add_argument('--cores', type=int, default=16, help='Number of cores to use')
    args = parser.parse_args()

    num_cores = args.cores

    # Create a list of arguments for each combination of sample, sim, and nsim
    args_list = [(sample_id, nsim) for sample_id in sample_idx for nsim in nsim_idx]

    # Use a multiprocessing Pool to process the samples in parallel
    with Pool(processes=num_cores) as pool:
        results = pool.map(process_sample_sim_nsim, args_list)

    all_results = []
    for result in results:
        if result is not None:
            all_results.extend(result)

    print(f"Processed {len(all_results)} entries")

    # Convert the data into a DataFrame
    df = pd.DataFrame(all_results)

    # Save the DataFrame to a CSV file
    df.to_csv('vns_dataset_threshold_sim.csv', index=False)

    print("Dataset compilation complete. Data saved to 'vns_dataset_threshold_sim.csv'")