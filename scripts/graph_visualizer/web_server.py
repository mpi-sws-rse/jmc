from fastapi import FastAPI, HTTPException, Response
from fastapi.responses import HTMLResponse, JSONResponse, FileResponse
from fastapi.staticfiles import StaticFiles
import json
import os
import sys
import logging

app = FastAPI(title="Graph Visualization Server")

graph_files = {}
log_files = {}


@app.get("/", response_class=HTMLResponse)
async def read_root():
    """Serve the main visualization page"""
    with open("template/index.html") as f:
        return HTMLResponse(content=f.read())


@app.get("/template/{file_path:path}")
async def read_static(file_path: str):
    """Serve static files"""
    return FileResponse(f"template/{file_path}")


@app.get("/api/graph/{graph:int}")
async def get_graph(graph: int):
    """Return the graph data"""
    if (graph < 0) or (graph not in graph_files):
        raise HTTPException(status_code=404, detail="Graph not found")
    try:
        with open(graph_files[graph]) as f:
            return JSONResponse(content=json.load(f))
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail="Graph data not found")


@app.get("/api/graphs")
async def get_graphs():
    """Get all the graph data"""
    max_graph = max(graph_files.keys())
    min_graph = min(graph_files.keys())
    return JSONResponse(content={"min_graph": min_graph, "max_graph": max_graph})


@app.get("/api/log/{graph:int}")
async def get_log(graph: int):
    """Return the log data"""
    if (graph < 0) or (graph not in log_files):
        raise HTTPException(status_code=404, detail="Log not found")
    try:
        with open(log_files[graph]) as f:
            return JSONResponse(content={"log": f.readlines()})
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail="Log data not found")


def _get_graph_code(graph_filename: str):
    filename = graph_filename.split(".")[0]
    split_names = filename.split("-")
    return int(split_names[-1])


def _is_valid_graph_file(graph_filename: str, guiding: bool):
    try:
        if guiding and "guiding" not in graph_filename:
            return False
        if not guiding and "guiding" in graph_filename:
            return False
        _get_graph_code(graph_filename)
        return True
    except Exception as e:
        return False


def read_graphs(graph_files_path: str, guiding: bool):
    """Read the graph files from the given path"""
    print("Reading" + ("" if not guiding else " guiding") + " graph files from:", graph_files_path)
    global graph_files
    graph_files = {}
    global log_files
    log_files = {}
    for file in os.listdir(graph_files_path):
        if file.endswith(".json") and _is_valid_graph_file(file, guiding):
            file_id = _get_graph_code(file)
            graph_files[file_id] = os.path.join(graph_files_path, file)
        elif file.endswith(".log") and _is_valid_graph_file(file, guiding):
            file_id = _get_graph_code(file)
            log_files[file_id] = os.path.join(graph_files_path, file)

    if len(graph_files.keys()) == 0:
        print("No valid graph files found in the specified directory.")
        sys.exit(1)


if __name__ == "__main__":
    if (len(sys.argv) != 2 and len(sys.argv) != 3):
        print("Usage: python web_server.py <graph_files_path> [--guiding]")
        sys.exit(1)
    graph_files_path = sys.argv[1]
    guiding = False
    if len(sys.argv) == 3 and sys.argv[2] == "--guiding":
        guiding = True
    if not os.path.exists(graph_files_path):
        print(f"Error: {graph_files_path} not found")
        sys.exit(1)

    read_graphs(graph_files_path, guiding)
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
