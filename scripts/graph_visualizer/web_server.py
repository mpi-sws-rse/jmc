from fastapi import FastAPI, HTTPException, Response
from fastapi.responses import HTMLResponse, JSONResponse, FileResponse
from fastapi.staticfiles import StaticFiles
import json
import os
import sys
import logging

app = FastAPI(title="Graph Visualization Server")

graph_files = {}

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
    if (graph < 0)  or (graph not in graph_files):
        raise HTTPException(status_code=404, detail="Graph not found")
    try:
        with open(graph_files[graph]) as f:
            return JSONResponse(content=json.load(f))
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail="Graph data not found")

@app.get("/api/graphs")
async def get_graphs():
    """Get all the graph data"""
    return JSONResponse(content=[k for k in graph_files.keys()])

def read_graphs(graph_files_path: str):
    """Read the graph files from the given path"""
    global graph_files
    graph_files = {}
    for file in os.listdir(graph_files_path):
        if file.endswith(".json"):
            file_id = int(file.split(".")[0].split("-")[1])
            graph_files[file_id] = os.path.join(graph_files_path,file)

if __name__ == "__main__":
    if (len(sys.argv) != 2):
        print("Usage: python web_server.py <graph_files_path>")
        sys.exit(1)
    graph_files_path = sys.argv[1]
    if not os.path.exists(graph_files_path):
        print(f"Error: {graph_files_path} not found")
        sys.exit(1)

    read_graphs(graph_files_path)
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)