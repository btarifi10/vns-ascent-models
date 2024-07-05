import shutil
import argparse
import os

DIR = os.path.dirname(os.path.realpath(__file__))
ASCENT_CODE_PATH = os.path.join(DIR, 'ascent_code')
MODELS_PATH = os.path.join(DIR, 'models')

if __name__ == "__main__":
    # get model name 
    parser = argparse.ArgumentParser(description="Transfer model config files to ascent_code_folder")
    parser.add_argument('--model-folder', type=str, required=True, help='The folder containing the config and samples files to be transferred')

    args = parser.parse_args()
    model_folder = f'{MODELS_PATH}/{args.model_folder}'

    # copy model config files to ascent_code folder
    shutil.rmtree(os.path.join(ASCENT_CODE_PATH, 'config', 'user'), ignore_errors=True)
    shutil.copytree(f'{model_folder}/config/user/runs', os.path.join(ASCENT_CODE_PATH, 'config', 'user', 'runs'), dirs_exist_ok=True)
    shutil.copytree(f'{model_folder}/config/user/sims', os.path.join(ASCENT_CODE_PATH, 'config', 'user', 'sims'), dirs_exist_ok=True)
    shutil.copytree(f'{model_folder}/config/user/mock_samples', os.path.join(ASCENT_CODE_PATH, 'config', 'user', 'mock_samples'), dirs_exist_ok=True)

    # copy model samples to ascent_code folder
    shutil.copytree(f'{model_folder}/samples', os.path.join(ASCENT_CODE_PATH, 'samples'))
