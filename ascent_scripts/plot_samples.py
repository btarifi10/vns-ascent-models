import os
import json

import matplotlib.pyplot as plt
from matplotlib_scalebar.scalebar import ScaleBar
from mpl_toolkits.axes_grid1.anchored_artists import AnchoredSizeBar

# plt.rcParams['font.family'] = 'DeJavu Serif'
# plt.rcParams['font.serif'] = ['Times New Roman']

SAMPLES_DATA_FILE = './sample_data.json'

def plot_sample(sample_data, sims, myelinated_flags, ax):
    # Plot nerve and fascicles
    ax.plot(sample_data['nerve']['x'], sample_data['nerve']['y'], 'k')
    ax.plot(sample_data['fascicle_outer']['x'], sample_data['fascicle_outer']['y'], 'g')
    ax.plot(sample_data['fascicle_inner']['x'], sample_data['fascicle_inner']['y'], 'tab:green')
    ax.axis('equal')
    ax.axis('off')

    # Plot fibres
    for sim in sims:
        sim_fibres = sample_data['fibres'][sim]['fibres']
        colour = 'r' if myelinated_flags[sim] else 'b'
        for fibre in sim_fibres:
            ax.add_artist(plt.Circle((fibre['x'], fibre['y']), fibre['diam'] / 2, color=colour, fill=True))

    # Add scale bar
    bar = AnchoredSizeBar(ax.transData, 50, '50 µm', 'lower right', frameon=False, size_vertical=0.5, fontproperties={'size': 12})
    ax.add_artist(bar) 
    


def main():
    with open(SAMPLES_DATA_FILE, 'r') as file:
        samples_data = json.load(file)

    samples = [10, 11, 12, 13, 14, 15]
    for sample in samples:
        fig, ax = plt.subplots(1, 1)
        plot_sample(samples_data[str(sample)], ['Unmyelinated AT', 'Myelinated AT'], {'Myelinated AT': True, 'Unmyelinated AT': False}, ax)
        plt.show()
        plt.savefig(f'./samples_imgs/{sample}.eps', dpi=500, bbox_inches='tight')
        plt.savefig(f'./samples_imgs/{sample}.png', dpi=500, bbox_inches='tight')



if __name__ == '__main__':
    main()