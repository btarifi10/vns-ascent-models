#!/bin/bash
#PBS -N import_sims
#PBS -J 0-9
#PBS -l select=1:ncpus=1:mem=2gb
#PBS -l walltime=01:00:00

cd $PBS_O_WORKDIR

module load tools/prod

module load comsol/6.0
module load java/oracle-jdk-11.0.10
module load anaconda3/personal

export LMCOMSOL_LICENSE_FILE=1718@iclic3.cc.imperial.ac.uk
export PATH=$PATH:$HOME/nrn/x86_64/bin

source activate ascent

python run import_n_sims ${PBS_ARRAY_INDEX}


