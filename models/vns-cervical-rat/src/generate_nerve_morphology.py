import numpy as np
from scipy.stats import truncnorm
import json

# Function to get truncated normal samples
def get_truncated_normal(mean=0, sd=1, low=0, upp=10, size=1):
    return truncnorm(
        (low - mean) / sd, (upp - mean) / sd, loc=mean, scale=sd).rvs(size)

# Function to calculate semi-minor axis (b) from semi-major axis (a) and area
def calculate_b(a, area):
    return area / (np.pi * a)

# Function to generate nerve and fascicle geometries
def generate_nerve_histology_samples(n_samples, nerve_mean_diameter, nerve_sd_diameter, fascicle_mean_diameter, fascicle_sd_diameter):
    samples = []
    min_distance = 5  # Minimum distance between the fascicle and the nerve boundary

    for _ in range(n_samples):
        # Generate nerve diameter and calculate area
        nerve_diameter = get_truncated_normal(mean=nerve_mean_diameter, sd=nerve_sd_diameter, low=0.5*nerve_mean_diameter, upp=1.5*nerve_mean_diameter, size=1)[0]
        nerve_radius = nerve_diameter / 2
        nerve_area = np.pi * nerve_radius ** 2

        # Sample semi-major axis (a) for nerve and calculate semi-minor axis (b)
        nerve_a = nerve_radius * np.random.uniform(0.9, 1.1)
        nerve_b = calculate_b(nerve_a, nerve_area)
        nerve_rotation = np.random.uniform(-5, 5)  # Minor rotation in degrees

        while True:
            # Generate fascicle diameter and calculate area
            fascicle_diameter = get_truncated_normal(mean=fascicle_mean_diameter, sd=fascicle_sd_diameter, low=0.5*fascicle_mean_diameter, upp=1.5*fascicle_mean_diameter, size=1)[0]
            fascicle_radius = fascicle_diameter / 2
            fascicle_area = np.pi * fascicle_radius ** 2

            # Sample semi-major axis (a) for fascicle and calculate semi-minor axis (b)
            fascicle_a = fascicle_radius * np.random.uniform(0.9, 1.1)
            fascicle_b = calculate_b(fascicle_a, fascicle_area)

            # Ensure fascicle fits inside the nerve considering the rotation and minimum distance
            max_offset_x = nerve_a - fascicle_a - min_distance
            max_offset_y = nerve_b - fascicle_b - min_distance

            if max_offset_x > 0 and max_offset_y > 0:
                fascicle_x, fascicle_y = None, None
                while True:
                    fascicle_x = np.random.uniform(-max_offset_x, max_offset_x)
                    fascicle_y = np.random.uniform(-max_offset_y, max_offset_y)
                    # Transform the fascicle position to account for nerve rotation
                    rotated_x = fascicle_x * np.cos(np.radians(nerve_rotation)) - fascicle_y * np.sin(np.radians(nerve_rotation))
                    rotated_y = fascicle_x * np.sin(np.radians(nerve_rotation)) + fascicle_y * np.cos(np.radians(nerve_rotation))
                    if (rotated_x**2 / (nerve_a - fascicle_a - min_distance)**2 + rotated_y**2 / (nerve_b - fascicle_b - min_distance)**2) <= 1:
                        break

                sample = {
                    "nerve_a": nerve_a,
                    "nerve_b": nerve_b,
                    "nerve_rotation": nerve_rotation,
                    "fascicle_x": fascicle_x,
                    "fascicle_y": fascicle_y,
                    "fascicle_a": fascicle_a,
                    "fascicle_b": fascicle_b
                }

                samples.append(sample)
                break

    return samples