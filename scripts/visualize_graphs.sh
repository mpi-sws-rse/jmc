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
    pip3 --cache-dir . install -r requirements.txt
else
    source venv/bin/activate
fi

# Check and ensure a command line argument is provided
if [ $# -eq 0 ]; then
    echo "No arguments provided. Please provide the path to the graph directory."
    exit 1
fi

GRAPH_DIR="../../$1"

# Check if the provided argument is a valid directory
if [ ! -d "$GRAPH_DIR" ]; then
    echo "Invalid directory: $GRAPH_DIR"
    exit 1
fi

# Check that the number of arguuments is not more than 2
if [ $# -gt 2 ]; then
    echo "Too many arguments provided. Usage: $0 <path_to_graph_directory> [--guiding]"
    exit 1
fi

# Run the visualizer
# Check if the second argument is --guiding
if [ $# -eq 2 ] && [ "$2" = "--guiding" ]; then
    python3 web_server.py $GRAPH_DIR --guiding
else
    python3 web_server.py $GRAPH_DIR
fi