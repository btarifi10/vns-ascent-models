import numpy as np
from scipy.stats import truncnorm
import json

# Function to get truncated normal samples
def get_truncated_normal(mean=0, sd=1, low=0, upp=10, size=1):
    return truncnorm(
        (low - mean) / sd, (upp - mean) / sd, loc=mean, scale=sd).rvs(size)

# Function to calculate semi-minor axis (b) from semi-major axis (a) and area
def calculate_b(a, area):
    return area / (np.pi * a/2)

# Function to generate nerve and fascicle geometries
def generate_nerve_histology_samples(n_samples, nerve_mean_diameter, nerve_sd_diameter, fascicle_mean_diameter, fascicle_sd_diameter):
    samples = []
    min_distance = 5  # Minimum distance between the fascicle and the nerve boundary

    for _ in range(n_samples):
        while True:
            try:
                # Generate nerve diameter and calculate area
                nerve_diameter = get_truncated_normal(mean=nerve_mean_diameter, sd=nerve_sd_diameter, low=0.5*nerve_mean_diameter, upp=1.5*nerve_mean_diameter, size=1)[0]
                nerve_radius = nerve_diameter / 2
                nerve_area = np.pi * nerve_radius ** 2

                # Sample semi-major axis (a) for nerve and calculate semi-minor axis (b)
                nerve_a = nerve_diameter * np.random.uniform(0.9, 1.1)
                nerve_b = calculate_b(nerve_a, nerve_area)

                # Generate fascicle diameter and calculate area
                fascicle_diameter = get_truncated_normal(mean=fascicle_mean_diameter, sd=fascicle_sd_diameter, low=0.5*fascicle_mean_diameter, upp=(nerve_a-min_distance), size=1)[0]
                fascicle_radius = fascicle_diameter / 2
                fascicle_area = np.pi * fascicle_radius ** 2

                # Sample semi-major axis (a) for fascicle and calculate semi-minor axis (b)
                fascicle_a = fascicle_diameter * np.random.uniform(0.9, 1.1)
                fascicle_b = calculate_b(fascicle_a, fascicle_area)

                # Ensure fascicle fits inside the nerve considering the minimum distance
                max_offset_x = nerve_a - fascicle_a - min_distance
                max_offset_y = nerve_b - fascicle_b - min_distance

                if max_offset_x > 0 and max_offset_y > 0:
                    break  # Exit the loop if valid values are obtained

            except Exception as e:
                continue

        fascicle_x, fascicle_y = 0, 0
        while True:
            fascicle_x = np.random.uniform(-10, 10)
            fascicle_y = np.random.uniform(-10, 10)
            if (fascicle_x**2 / (nerve_a - fascicle_a - min_distance)**2 + fascicle_y**2 / (nerve_b - fascicle_b - min_distance)**2) <= 1:
                break

        sample = {
            "nerve_a": nerve_a,
            "nerve_b": nerve_b,
            "nerve_rotation": 0,  # No rotation
            "fascicle_x": fascicle_x,
            "fascicle_y": fascicle_y,
            "fascicle_a": fascicle_a,
            "fascicle_b": fascicle_b
        }

        samples.append(sample)

    return samples