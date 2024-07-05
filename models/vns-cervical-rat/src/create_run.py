import json
import os
import shutil
import argparse

from generate_ascent_config import create_mock_sample_cfg, create_run_cfg, create_sample_model_cfg
from generate_nerve_morphology import generate_nerve_histology_samples


DIR = os.path.dirname(os.path.realpath(__file__))
MORPH_SUMMARY_PATH = os.path.join(DIR, 'morphology_summary.json')

def save_cfg_to_file(json_data, file_path):
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    with open(file_path, 'w') as json_file:
        json.dump(json_data, json_file, indent=4)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate nerve histology samples and create run configurations.")
    parser.add_argument('--samples', type=int, required=True, help='The number of samples to generate')
    parser.add_argument('--dest', type=str, default='.', help='The destination of the configs')
    
    args = parser.parse_args()
    n_samples = args.samples
    dest = args.dest

    with open(MORPH_SUMMARY_PATH, 'r') as file:
        morph = json.load(file)
    
    nerve_diameter_mean = morph['cervical']['nerve_diameter_um']['mean']
    nerve_diameter_sd = morph['cervical']['nerve_diameter_um']['stddev']
    fascicle_diameter_mean = morph['cervical']['fascicle_diameter_um']['mean']
    fascicle_diameter_sd = morph['cervical']['fascicle_diameter_um']['stddev']

    samples = generate_nerve_histology_samples(
        n_samples=n_samples,
        nerve_mean_diameter=nerve_diameter_mean,
        nerve_sd_diameter=nerve_diameter_sd,
        fascicle_mean_diameter=fascicle_diameter_mean,
        fascicle_sd_diameter=fascicle_diameter_sd
    )

    for i, sample in enumerate(samples):
        sample_cfg, model_cfg = create_sample_model_cfg(i)
        mock_sample_cfg = create_mock_sample_cfg(i, sample['nerve_a'], sample['nerve_b'], sample['nerve_rotation'], sample['fascicle_x'], sample['fascicle_y'], sample['fascicle_a'], sample['fascicle_b'])
        run_cfg = create_run_cfg(i)

        # Save sample.json files
        save_cfg_to_file(sample_cfg, f'{dest}/samples/{i}/sample.json')

        # Save model.json files
        for i, model_json in enumerate(model_cfg):
            save_cfg_to_file(model_json, f'{dest}/samples/{i}/models/0/model.json')

        # Save mock_sample json file
        save_cfg_to_file(mock_sample_cfg, f'{dest}/config/user/mock_samples/{i}.json')

        # Save run json file
        save_cfg_to_file(run_cfg, f'{dest}/config/user/runs/{i}.json')

    # Copy sims/0.json and sims/1.json
    os.makedirs(f'{dest}/config/user/sims', exist_ok=True)
    shutil.copy(f'{DIR}/sim_0.json', f'{dest}/config/user/sims/0.json')
    shutil.copy(f'{DIR}/sim_1.json', f'{dest}/config/user/sims/1.json')