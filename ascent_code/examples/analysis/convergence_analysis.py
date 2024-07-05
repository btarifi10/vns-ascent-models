"""Use this script to run a convergence study.

The copyrights of this software are owned by Duke University.
Please refer to the LICENSE.txt and README.txt files for licensing instructions.
The source code can be found on the following GitHub repository: https://github.com/wmglab-duke/ascent.

Threshold error will be calculated for each model and sample with respect to the reference model.
RUN THIS FROM REPOSITORY ROOT
"""

import os
import sys

sys.path.append(os.path.sep.join([os.getcwd(), '']))

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sb

from src.core.query import Query

samples = [1000, 10, 20]
models = [0, 1, 2, 10, 11, 20, 21, 30]
sims = [0]
reference_model = 0


def pe(correct, est):
    """Calculate the percent error.

    :param correct: correct value
    :param est: estimated value
    :return: percent error
    """
    return 100 * abs(est - correct) / correct


if reference_model not in models:
    models.append(reference_model)

data = []

# %% Pull threshold data
for sim in sims:
    q = Query(
        {
            'partial_matches': False,
            'include_downstream': True,
            'indices': {'sample': samples, 'model': models, 'sim': [sim]},
        }
    ).run()

    data.append(q.threshold_data())
data = pd.concat(data)

# %% Calculate error values
data['error'] = np.nan
for i in range(len(data)):
    row = data.iloc[i]
    est = float(row.threshold)
    correct = float(
        data[
            (data["model"] == reference_model)
            & (data["sample"] == row["sample"])
            & (data["index"] == row["index"])
            & (data["sim"] == row['sim'])
            & (data["nsim"] == row['nsim'])
        ]['threshold']
    )
    data.iloc[i, -1] = pe(correct, est)


# %% Generate convergence plot
g = sb.catplot(
    x="sample",
    y='error',
    hue='model',
    col="nsim",
    palette='colorblind',
    data=data,
    kind="strip",
    height=5,
    aspect=0.4,
    linewidth=1,
)

plt.subplots_adjust(top=0.88)
plt.suptitle(f'Convergence Error - Reference Model: {reference_model}')
g.savefig('out/analysis/convergence.png', dpi=400)
