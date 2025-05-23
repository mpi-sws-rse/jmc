#!/usr/bin/env bash

# Check if a directory is provided
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 path/to/your/json/files"
    exit 1
fi

# Directory containing JSON files from the argument
DIRECTORY="$1"

# Check if the provided argument is a directory
if [ ! -d "$DIRECTORY" ]; then
    echo "Error: $DIRECTORY is not a valid directory."
    exit 1
fi

# Change to the specified directory
cd "$DIRECTORY" || exit

# Create an array to hold file names
files=(*.json)

# Loop through each pair of files
for ((i = 0; i < ${#files[@]}; i++)); do
    for ((j = i + 1; j < ${#files[@]}; j++)); do
        file1="${files[i]}"
        file2="${files[j]}"

        # Compare the two JSON files without sorting
        if diff -q "$file1" "$file2" > /dev/null; then
            echo "Identical files: $file1 and $file2"
        fi
    done
done