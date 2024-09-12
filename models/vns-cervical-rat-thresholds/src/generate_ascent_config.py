import os
import json


DIR = os.path.dirname(os.path.realpath(__file__))
with open(f'{DIR}/model_template.json', 'r') as file:
    MODEL_TEMPLATE = json.load(file)

with open(f'{DIR}/sample_template.json', 'r') as template_file:
    SAMPLE_TEMPLATE = json.load(template_file)

with open(f'{DIR}/run_template.json', 'r') as run_template_file:
    RUN_TEMPLATE = json.load(run_template_file)

# Function to create the mock_sample json
def create_mock_sample_cfg(sample_id, nerve_a, nerve_b, nerve_rotation, fascicle_x, fascicle_y, fascicle_a, fascicle_b):
    return {
        "global": {
            "NAME": f"Rat-VN-{sample_id}"
        },
        "scalebar_length": 200,
        "nerve": {
            "a": nerve_a,
            "b": nerve_b,
            "rot": nerve_rotation
        },
        "figure": {
            "fig_margin": 1.2,
            "fig_dpi": 1000
        },
        "populate": {
            "mode": "EXPLICIT",
            "min_fascicle_separation": 5,
            "Fascicles": [
                {
                    "centroid_x": fascicle_x,
                    "centroid_y": fascicle_y,
                    "a": fascicle_a,
                    "b": fascicle_b,
                    "rot": 0
                }
            ]
        }
    }

# Function to create the run json
def create_run_cfg(sample_id):
    run_json = RUN_TEMPLATE.copy()
    run_json["sample"] = sample_id
    run_json["pseudonym"] = f"run:Rat-VN-{sample_id}"
    return run_json

def create_sample_model_cfg(sample_id):
    sample_json = SAMPLE_TEMPLATE.copy()
    sample_json["sample"] = f"Rat-VN-{sample_id}"
    sample_json["pseudonym"] = f"sample:Rat-VN-{sample_id}"

    model_json = MODEL_TEMPLATE.copy()
    model_json["pseudonym"] = f"mdoel:Rat-VN-{sample_id}"

    return sample_json, [model_json]

