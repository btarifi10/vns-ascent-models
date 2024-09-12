#!/bin/bash
#PBS -N rm_files
#PBS -l select=1:ncpus=1:mem=8gb
#PBS -l walltime=08:00:00

cd $PBS_O_WORKDIR

module load tools/prod

cd samples
rm -rf 15/ 16/ 17/ 18/ 19/


