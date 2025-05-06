import os
import numpy as np
from multiprocessing import Pool

def compute_edit_distance(graph1, graph2, threshold):
    print(f"Computing edit distance between graphs {graph1['name']} and {graph2['name']}")
    string1 = graph1["data"]
    string2 = graph2["data"]
    matrix = np.array([[0] * (len(string2) + 1) for _ in range(len(string1) + 1)])

    for i in range(len(string1) + 1):
        matrix[i][0] = i

    for j in range(len(string2) + 1):
        matrix[0][j] = j

    for i in range(1, len(string1) + 1):
        for j in range(1, len(string2) + 1):
            if string1[i - 1] == string2[j - 1]:
                cost = 0
            else:
                cost = 1
            matrix[i][j] = min(matrix[i - 1][j] + 1,    # Deletion
                               matrix[i][j - 1] + 1,    # Insertion
                               matrix[i - 1][j - 1] + cost)
            if (matrix[i][j] > threshold):
                return None

    return matrix[len(string1)][len(string2)]

def find_similar_graphs(graphs, threshold):
    similar_graphs = []
    with Pool(processes=4) as pool:
        similar_graphs = pool.starmap(compute_edit_distance, [(graphs[i], graphs[j], threshold) for i in range(len(graphs)) for j in range(i + 1, len(graphs))])

    return [g for g in similar_graphs if g is not None]

def read_graphs(file_path, count):
    graphs = []
    for i in range(count):
        with open(file_path+str(i+1)+".json", 'r') as file:
            graphs.append({"name": str(i+1), "data": file.readlines()[0]})
    return graphs

def main():

    # Define the path to the directory containing the graph files
    directory_path = "../../core/build/test-results/jmc-report/random-5-PT1M/"

    # Define the number of graphs to read
    num_graphs = 125

    # Read the graphs from the specified directory
    graphs = read_graphs(directory_path, num_graphs)

    print("Graphs read successfully.")

    # Define the threshold for edit distance
    threshold = 10

    # Find similar graphs based on edit distance
    similar_graphs = find_similar_graphs(graphs, threshold)

    # Print the results
    for i, j, distance in similar_graphs:
        print(f"Graphs {i} and {j} are similar with an edit distance of {distance}")

if __name__ == "__main__":
    main()