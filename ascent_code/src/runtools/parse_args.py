"""Parse command line arguments.

The copyrights of this software are owned by Duke University.
Please refer to the LICENSE and README.md files for licensing instructions.
The source code can be found on the following GitHub repository: https://github.com/wmglab-duke/ascent
"""

import argparse
import sys

from config.system import _version

# Set up parser and top level args
parser = argparse.ArgumentParser(
    description='ASCENT: Automated Simulations to Characterize Electrical Nerve Thresholds'
)
parser.add_argument('-v', '--verbose', action='store_true', help='verbose printing')
parser.add_argument('--version', action='version', version=f'ASCENT version {_version.__version__}')
parser.add_argument(
    '-l',
    '--list',
    choices=['runs', 'samples', 'sims'],
    help='List all available indices for the specified option',
)

# add subparsers for each script
subparsers = parser.add_subparsers(help='which script to run', dest='script')

# add parser for pipeline
pipeline_parser = subparsers.add_parser('pipeline', help='main ASCENT pipeline')
pipeline_parser.add_argument(
    'run_indices',
    type=int,
    nargs='+',
    help='Space separated indices to run the pipeline over',
)
pipeline_parser.add_argument(
    '-b',
    '--break-point',
    choices=[
        "pre_geom_run",
        "post_geom_run",
        "pre_java",
        "post_mesh_distal",
        "pre_mesh_distal",
        "post_material_assign",
        "pre_loop_currents",
        "pre_mesh_proximal",
        "post_mesh_proximal",
        "pre_solve",
    ],
    help='Point in pipeline to exit and continue to next run',
)
pipeline_parser.add_argument(
    '-w',
    '--wait-for-license',
    type=float,
    help="Wait the specified number of hours for a comsol license to become available.",
)
pipeline_parser.add_argument(
    '-P',
    '--partial-fem',
    choices=["cuff_only", "nerve_only"],
    help="Only generate the specified geometry.",
)
pipeline_parser.add_argument(
    '-E',
    '--export-behavior',
    choices=["overwrite", "error", "selective"],
    help="Behavior if n_sim export encounters extant data. Default is selective.",
)
pipeline_parser.add_argument(
    '-e',
    '--endo-only-solution',
    action='store_true',
    help="Store basis solutions for endoneurial geometry ONLY",
)
pipeline_parser.add_argument(
    '-r',
    '--render-deform',
    action='store_true',
    help="Pop-up window will render deformation operations",
)
pipeline_parser.add_argument(
    '-S',
    '--auto-submit',
    action='store_true',
    help="Automatically submit fibers after each run",
)
prog_group = pipeline_parser.add_mutually_exclusive_group()
prog_group.add_argument(
    '-c',
    '--comsol-progress',
    action='store_true',
    help="Print COMSOL progress to stdout",
)
prog_group.add_argument(
    '-C',
    '--comsol-progress-popup',
    action='store_true',
    help="Show COMSOL progress in a pop-up window",
)
# add parser for tidy samples
ts_parser = subparsers.add_parser('tidy_samples', help='Remove specified files from Sample directories')
ts_parser.add_argument('sample_indices', nargs='+', type=int, help='Space separated sample indices to tidy')
ts_parser.add_argument('-f', '--filename', type=str, help='Filename to clear')

# add parser for import n sims
nsims_parser = subparsers.add_parser('import_n_sims', help='Move NEURON outputs into ASCENT directories for analysis')
nsims_parser.add_argument('run_indices', nargs='+', type=int, help='Space separated run indices to import')
nsims_group = nsims_parser.add_mutually_exclusive_group()
nsims_group.add_argument(
    '-f',
    '--force',
    action='store_true',
    help='Import n_sims even if all thresholds are not found',
)
nsims_group.add_argument(
    '-D',
    '--delete-nsims',
    action='store_true',
    help='After importing delete n_sim folder from NSIM_EXPORT_PATH',
)

# add clean samples parser
cs_parser = subparsers.add_parser(
    'clean_samples',
    help='Remove all files except those specified from Sample directories',
)
cs_parser.add_argument(
    '-R', '--full-reset', action='store_true', help='Clear all files except sample.json and model.json'
)
cs_parser.add_argument(
    'sample_indices',
    nargs='+',
    type=int,
    help='Space separated sample indices to clean',
)

# add mock sample parser
mmg_parser = subparsers.add_parser('mock_morphology_generator', help='Generate mock morpology for an ASCENT run')
mmg_parser.add_argument('mock_sample_index', type=int, help='Mock Sample Index to generate')

# Parser for install.py
install_parser = subparsers.add_parser('install', help='install ASCENT')
install_parser.add_argument('--no-conda', action='store_true', help='Skip conda portion of installation')

# parser for env setup
env_parser = subparsers.add_parser('env_setup', help='Set ASCENT environment variables')

# parser for build_dataset
bd_parser = subparsers.add_parser('build_dataset', help='Export dataset from ASCENT runs in SPARC format')
bd_parser.add_argument(
    'dataset_indices',
    nargs='+',
    type=int,
    help='Space separated dataset indices to export',
)
# add subparsers for each script
bd_subcommands = bd_parser.add_subparsers(help='build_dataset stage', dest='stage', required=True)
bd_query_parser = bd_subcommands.add_parser('query', help='Use query criteria to build excel output')
bd_generate_parser = bd_subcommands.add_parser('generate', help='Generate dataset (keeps files) from excel output')
bd_generate_parser.add_argument('-f', '--force', action='store_true', help='Overwrite existing dataset')


def parse():
    """Parse all args.

    :returns: args
    :rtype: Namespace
    """

    def g0(args, argstring):
        """Check that an argument is greater than 0.

        :param args: args object.
        :param argstring: string of argument to check.
        """
        if hasattr(args, argstring) and getattr(args, argstring) is not None and getattr(args, argstring) <= 0:
            sys.exit(f'Arguments for {argstring} must be greater than 0')

    # parse arguments
    args = parser.parse_args()
    g0(args, 'wait_for_license')

    if not len(sys.argv) > 1:
        parser.print_help()
        sys.exit()

    return args
