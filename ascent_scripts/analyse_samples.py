import os
import json

# Function to read coordinates from a file
def read_coordinates(file_path):
    coordinates = []
    with open(file_path, 'r') as file:
        first_line = file.readline().strip()
        for line in file:
            x, y = map(float, line.split())
            coordinates.append((x, y))
    return coordinates

def get_sample_data(sample, sims, sim_title_map):
    sample_folder = f'./samples/{sample}'
    nerve_file = f'{sample_folder}/slides/0/0/sectionwise2d/nerve/0/0.txt'
    fascicle_outer = f'{sample_folder}/slides/0/0/sectionwise2d/fascicles/0/outer/0.txt'
    fascicle_inner = f'{sample_folder}/slides/0/0/sectionwise2d/fascicles/0/inners/0.txt'

    shape_files = {
        'nerve': nerve_file,
        'fascicle_outer': fascicle_outer,
        'fascicle_inner': fascicle_inner
    }
    nerve_data = {}

    for shape_type, shape_file in shape_files.items():
        coords = read_coordinates(shape_file)
        x, y = zip(*coords)
        nerve_data[shape_type] = {'x': list(x), 'y': list(y)}

    sim_fibre_data = {}
    for sim in sims:
        sim_fibres = f'{sample_folder}/models/2/sims/{sim}/fibersets/0'
        diams_file = os.path.join(sim_fibres, 'diams.txt')
        diameters = []
        with open(diams_file, 'r') as file:
            for line in file:
                diameters.append(float(line.strip()))
        fibres_data = []

        # Process .dat files and plot circles
        for file_name in sorted(os.listdir(sim_fibres)):
            if file_name.endswith('.dat'):
                file_index = int(file_name.split('.')[0])  # Extract the index from the filename
                # Make sure the file is not empty
                with open(f'{sim_fibres}/{file_name}', 'r') as file:
                    first_line = file.readline().strip()
                    coords = file.readline().strip()
                    x, y, _ = map(float, coords.split())
                fibres_data.append({'x': x, 'y': y, 'diam': diameters[file_index]})

        sim_fibre_data[sim_title_map[sim]] = {
            'sim_id': sim,
            'fibres': fibres_data
        }

    # Save data to JSON
    data = {
        **nerve_data,
        "fibres": sim_fibre_data
    }

    return data


samples = [10, 11, 12, 13, 14, 15]
sims = [5, 6, 7, 8]
sim_title_map = {
    5: 'Myelinated AT',
    6: 'Unmyelinated AT',
    7: 'Myelinated FA',
    8: 'Unmyelinated FA'
}
all_sample_data = {}
for sample in samples:
    sample_data = get_sample_data(sample, sims, sim_title_map)
    all_sample_data[sample] = sample_data

with open('sample_data.json', 'w') as json_file:
    json.dump(all_sample_data, json_file, indent=4)
