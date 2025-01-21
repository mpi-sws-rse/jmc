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

GRAPH_DIR="../../build/test-results/jmc-report"

# Run the visualizer
python3 web_server.py $GRAPH_DIR