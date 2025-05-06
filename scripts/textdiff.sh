#!/bin/bash

# Check if a directory is provided
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 path/to/your/text/files"
    exit 1
fi

# Directory containing text files from the argument
DIRECTORY="$1"

# Check if the provided argument is a directory
if [ ! -d "$DIRECTORY" ]; then
    echo "Error: $DIRECTORY is not a valid directory."
    exit 1
fi

# Change to the specified directory
cd "$DIRECTORY" || exit

# Create an array to hold file names
files=(*.txt)

# Loop through each pair of files
for ((i = 0; i < ${#files[@]}; i++)); do
    for ((j = i + 1; j < ${#files[@]}; j++)); do
        file1="${files[i]}"
        file2="${files[j]}"

        # Compare the two text files and count the number of redundant files
        if diff -q "$file1" "$file2" > /dev/null; then
            echo "Identical files: $file1 and $file2"
        fi


        # If they are not identical, identify the files name which have the difference is at most 5 lines
#        if ! diff -q "$file1" "$file2" > /dev/null; then
#            diff_output=$(diff "$file1" "$file2")
#            # Count the number of lines in the diff output
#            line_count=$(echo "$diff_output" | wc -l)
#            if [ "$line_count" -le 5 ]; then
#                echo "Files with minor differences: $file1 and $file2"
#            fi
#        fi
    done
done