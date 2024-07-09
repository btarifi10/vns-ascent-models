#!/bin/bash
#PBS -N rat_vn_pipeline
#PBS -l select=1:ncpus=128:mem=128gb
#PBS -l walltime=08:00:00

cd $PBS_O_WORKDIR

module load tools/prod

module load comsol/6.0
module load java/oracle-jdk-11.0.10
module load anaconda3/personal

export LMCOMSOL_LICENSE_FILE=1718@iclic3.cc.imperial.ac.uk

source activate ascent

start_time=$(date +%s)

touch pipeline.log

python run pipeline 10 11 12 13 14 15 16 17 18 19 >> pipeline.log

end_time=$(date +%s)
elapsed_time=$(( end_time - start_time ))

echo "Elapsed time: $elapsed_time seconds"

pb push -d 0 -t "Pipeline run complete" "$(cat pipeline.log)"

