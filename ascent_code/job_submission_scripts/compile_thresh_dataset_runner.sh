#!/bin/bash
#PBS -N compile_thresholds_dataset
#PBS -l select=1:ncpus=64:mem=64gb
#PBS -l walltime=04:00:00

cd $PBS_O_WORKDIR

module load tools/prod

module load anaconda3/personal
source activate ascent

python compile_dataset_thresholds.py --cores 64


