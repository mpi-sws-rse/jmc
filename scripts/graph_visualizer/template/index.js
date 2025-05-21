function plot_graph(graphData) {
    // Transform data for D3\
    const links = [];
    const nodeMap = new Map();
    const edgeTypes = new Set();
    const nodeTypes = new Set();
    var num_tasks = 0;
    var max_timestamp = 0;

    // Create nodes
    Object.entries(graphData.nodes).forEach(([id, data]) => {
        const node = {
            id,
            type: data.event.type,
            taskId: data.event.key.taskId,
            timestamp: data.event.key.timestamp,
            location: data.event.location?.sharedObject,
            attributes: data.event.attributes,
        };
        if (node.taskId > num_tasks) {
            num_tasks = node.taskId;
        }
        if (node.timestamp > max_timestamp) {
            max_timestamp = node.timestamp;
        }
        nodeTypes.add(node.type);
        nodeMap.set(id, node);
    });

    const initNode = "{null, null}";

    // Create links
    Object.entries(graphData.nodes).forEach(([sourceId, data]) => {
        if (data.edges) {
            Object.entries(data.edges).forEach(([edgeType, targets]) => {
                edgeTypes.add(edgeType);
                targets.forEach(targetId => {
                    if (edgeType === "coherency" && sourceId === initNode) {
                        // Skip the init node for coherency edges
                        return;
                    }
                    links.push({
                        source: sourceId,
                        target: targetId,
                        type: edgeType
                    });
                })
            })
        }
    });

    var edgeEnterDiv = d3.select("#edge-selection")
        .selectAll(".edge-input")
        .data(edgeTypes.keys())
        .enter().append("div");

    edgeEnterDiv.append("input")
        .attr("type", "checkbox")
        .attr("id", d => `edge-${d}`)
        .attr("value", d => d)
        .attr("class", "edge-input")
        .attr("checked", d => d === "readsFrom" || d === "programOrder" ? true : null)
        .on("change", function (event, d) {
            if (event.target.checked) {
                d3.selectAll(`.link.${d}`).style("display", "block");
            } else {
                d3.selectAll(`.link.${d}`).style("display", "none");
            }
        });

    edgeEnterDiv.append("label")
        .attr("for", d => `edge-${d}`)
        .text(d => d);

    // Set up the SVG
    const width = d3.select("#graph").node().getBoundingClientRect().width;
    const height = window.innerHeight * 0.9;

    const svg = d3.select("#graph")
        .append("svg")
        .attr("width", width)
        .attr("height", height);

    // Add zoom behavior
    const g = svg.append("g");
    svg.call(d3.zoom()
        .scaleExtent([0.1, 4])
        .on("zoom", (event) => {
            g.attr("transform", event.transform);
        }));

    // Color scale for edge types
    const edgeColorScale = d3.scaleOrdinal(edgeTypes.keys(), d3.schemeTableau10);
    const nodeColorScale = d3.scaleOrdinal(nodeTypes.keys(), d3.schemeSet1);

    // Create arrow markers for different edge types
    svg.append("defs").selectAll("marker")
        .data(edgeTypes.keys())
        .enter().append("marker")
        .attr("id", d => `arrow-${d}`)
        .attr("viewBox", "0 -5 10 10")
        .attr("refX", 20)
        .attr("refY", 0)
        .attr("markerWidth", 6)
        .attr("markerHeight", 6)
        .attr("orient", "auto")
        .append("path")
        .attr("d", "M0,-5L10,0L0,5")
        .attr("fill", d => edgeColorScale(d));

    // Create tooltip
    const tooltip = d3.select("body")
        .append("div")
        .attr("class", "tooltip")
        .style("opacity", 0);

    // Create the force simulation
    // const simulation = d3.forceSimulation(nodes)
    //     .force("link", d3.forceLink(links).id(d => d.id).distance(100))
    //     .force("charge", d3.forceManyBody().strength(-300))
    //     .force("center", d3.forceCenter(width / 2, height / 2))
    //     .force("x", d3.forceX(width / 2).strength(0.1))
    //     .force("y", d3.forceY(height / 2).strength(0.1));

    const xOffset = Math.max(width / (num_tasks + 1), 100);
    const yOffset = Math.max((height - 100) / (max_timestamp + 1), 100);

    const xScale = (taskId) => taskId == null ? (width / 2) : (taskId * xOffset + 10);
    const yScale = (timestamp) => timestamp == null ? 10 : (timestamp * yOffset + 100);
    // Draw links
    const link = g.selectAll(".link")
        .data(links)
        .enter().append("line")
        .attr("class", d => `link ${d.type}`)
        .attr("stroke", d => edgeColorScale(d.type))
        .attr("marker-end", d => `url(#arrow-${d.type})`)
        .attr("x1", d => xScale(nodeMap.get(d.source).taskId))
        .attr("y1", d => yScale(nodeMap.get(d.source).timestamp))
        .attr("x2", d => {
            let targetNode = nodeMap.get(d.target);
            if (targetNode == null) {
                console.log("Target node is null", d);
            }
            return xScale(nodeMap.get(d.target).taskId);
        })
        .attr("y2", d => yScale(nodeMap.get(d.target).timestamp))

    for (edgeType of edgeTypes.keys()) {
        if (edgeType !== "readsFrom" && edgeType !== "programOrder") {
            d3.selectAll(`.link.${edgeType}`).style("display", "none");
        }
    }

    // Draw nodes
    const node = g.selectAll(".node")
        .data(nodeMap.values())
        .enter().append("g")
        .attr("class", "node")
        .attr("transform", d => `translate(${xScale(d.taskId)}, ${yScale(d.timestamp)})`);

    node.append("circle")
        .attr("r", 10)
        .attr("fill", d => nodeColorScale(d.type));

    node.append("text")
        .attr("dx", 12)
        .attr("dy", ".35em")
        .text(d => {
            let node_type = d.type;
            if (node_type === "NOOP") {
                if (Object.hasOwn(d.attributes, "thread_start")) {
                    node_type = "THREAD_START";
                } else if (Object.hasOwn(d.attributes, "thread_finish")) {
                    node_type = "THREAD_END";
                } else if (Object.hasOwn(d.attributes, "thread_join")) {
                    node_type = "THREAD_JOIN";
                }
            }
            return `${node_type} (${d.taskId}, ${d.timestamp})`
        });

    // Add hover effects
    node.on("mouseover", function (event, d) {
        tooltip.transition()
            .duration(200)
            .style("opacity", .9);
        tooltip.html(`
                Type: ${d.type}<br/>
                Task ID: ${d.taskId}<br/>
                Timestamp: ${d.timestamp}<br/>
                ${d.location ? `Location: ${d.location}` : ''}
            `)
            .style("left", (event.pageX + 10) + "px")
            .style("top", (event.pageY - 28) + "px");
    })
        .on("mouseout", function (d) {
            tooltip.transition()
                .duration(500)
                .style("opacity", 0);
        });

}

function load_graph(graphName) {
    fetch(`/api/graph/${graphName}`)
        .then(response => response.json())
        .then(data => {
            document.getElementById("graph").innerHTML = '';
            plot_graph(data);
        });
}

function load_log(graphName) {
    fetch(`/api/log/${graphName}`)
        .then(response => response.json())
        .then(data => {
            d3.select('#log').selectAll("p").data(data.log).enter().append("p").attr("class", "box-content").text(d => d);
        }).catch((error) => {});
}

function update_graph_selection(selectElement) {
    const graphName = selectElement.value;
    load_graph(graphName);
    load_log(graphName);
}

// Load graph list and display as items
function load_graphs() {
    const graphList = document.getElementById("graphs");
    fetch('/api/graphs')
        .then(response => response.json())
        .then(data => {
            let minGraph = data.min_graph;
            let maxGraph = data.max_graph;
            [...Array(maxGraph - minGraph + 1).keys()].map(i => i + minGraph).forEach(graph => {
                const item = document.createElement("option");
                item.setAttribute("value", graph);
                item.innerHTML = `${graph}`;
                graphList.appendChild(item);
            });
        });
}

$(() => {
    load_graphs();
})