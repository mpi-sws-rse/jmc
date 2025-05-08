#!/usr/bin/env bash

# Check if the script is run from the correct directory
if [ ! -d "scripts/graph_visualizer" ]; then
    echo "Please run this script from the root directory of the project."
    exit 1
fi

VISUALIZER_DIR="scripts/graph_visualizer"
cd $VISUALIZER_DIR

# Setup venv if not
if [ ! -d "venv" ]; then
    python3 -m venv venv
    source venv/bin/activate
    pip3 install -r requirements.txt
else
    source venv/bin/activate
fi

# Check and ensure a command line argument is provided
if [ $# -eq 0 ]; then
    echo "No arguments provided. Please provide the path to the graph directory."
    exit 1
fi

# Check if the provided argument is a valid directory
if [ ! -d "$1" ]; then
    echo "Invalid directory: $1"
    exit 1
fi

GRAPH_DIR=$1

# Run the visualizer
python3 web_server.py $GRAPH_DIR