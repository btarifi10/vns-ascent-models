#!/bin/bash
#PBS -N compile_waveforms_dataset
#PBS -l select=1:ncpus=64:mem=32gb
#PBS -l walltime=02:00:00

cd $PBS_O_WORKDIR

module load tools/prod

module load anaconda3/personal
source activate ascent

python compile_dataset.py --cores 64


