import os
import pandas as pd

def main(folder):
    counts = []
    n_sims = [f for f in os.listdir(folder) if os.path.isdir(os.path.join(folder, f))]
    for dir in n_sims:
        input_folder = os.path.join(folder, dir, 'data', 'inputs')
        output_folder = os.path.join(folder, dir, 'data', 'outputs')

        n_inputs = len(os.listdir(input_folder))
        n_outputs = len(os.listdir(output_folder))
        counts.append((dir, n_inputs, n_outputs))

    return counts

if __name__ == '__main__':
    folder = os.path.join('export', 'n_sims')
    counts = sorted(main(folder), key=lambda x: x[0])
    
    counts_df = pd.DataFrame(counts, columns=['n_sim', 'inputs', 'outputs'])
    counts_df.to_csv('file_counts.csv', index=False)

    print("Counts saved to file_counts.csv")