#!/bin/bash
#PBS -l select=1:ncpus=200:mem=128gb
#PBS -l walltime=07:50:00
#PBS -N rat_vn_submit

cd $PBS_O_WORKDIR

module load tools/prod

module load comsol/6.0
module load java/oracle-jdk-11.0.10
module load anaconda3/personal

export LMCOMSOL_LICENSE_FILE=1718@iclic3.cc.imperial.ac.uk
export PATH=$PATH:$HOME/nrn/x86_64/bin

source activate ascent

start_time=$(date +%s)

python submit.py 0 -s -n 196

end_time=$(date +%s)
elapsed_time=$(( end_time - start_time ))

echo "Elapsed time: $elapsed_time seconds"

pb push -d 0 -t "Neuron jobs complete"

