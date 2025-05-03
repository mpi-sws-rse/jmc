#!/usr/bin/env bash

VISUALIZER_DIR="graph_visualizer"
cd $VISUALIZER_DIR

# Setup venv if not
if [ ! -d "venv" ]; then
    python3 -m venv venv
    source venv/bin/activate
    pip3 install -r requirements.txt
else
    source venv/bin/activate
fi

# Check if there are any arguments


GRAPH_DIR="../../core/build/test-results/jmc-report"
if [ $# -eq 1 ]; then
    GRAPH_DIR="../$1"
fi

# Run the visualizer
python3 web_server.py $GRAPH_DIR