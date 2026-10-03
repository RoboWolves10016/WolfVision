/*
 * Copyright (c) 2026 Clarkston RoboWolves 10016
 * SPDX-License-Identifier: MIT
 */

"use strict";

const $ = (selector) => document.querySelector(selector);
const world = $("#world");
const wires = $("#wires");
const viewer = $("#viewer");

/** Node types by name, as the robot reports them. */
const specs = {};
/** The graph being edited: the same shape as a graph file. */
let graph = { version: 1, nodes: [] };
/** The element of every node, by node id. */
const elements = new Map();
let status = { nodes: {} };
let selected = null;
/** The id of the Threshold node whose range is being picked from the image, or null. */
let picking = null;
let pushTimer = 0;
let pushError = null;

const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name);
const typeColour = (type) => (type ? css("--" + type) : css("--dim"));
const nodeById = (id) => graph.nodes.find((node) => node.id === id);

function make(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

async function api(path, options) {
  const response = await fetch(path, options);
  const body = await response.json();
  if (!response.ok) throw Object.assign(new Error(body.error || response.statusText), body);
  return body;
}

// ---- Sending the graph to the robot ----

function schedulePush() {
  clearTimeout(pushTimer);
  pushTimer = setTimeout(push, 60);
}

async function push() {
  try {
    await api("/api/graph", { method: "PUT", body: JSON.stringify(graph) });
    pushError = null;
  } catch (error) {
    pushError = { message: error.message, node: error.node };
  }
  showProblems();
}

function showProblems() {
  const problem = pushError || status.error;
  const banner = $("#banner");
  banner.hidden = !problem;
  if (problem) {
    const where = problem.node ? problem.node + ": " : "";
    const kept = pushError ? " The robot keeps running the last graph that worked." : "";
    banner.textContent = where + problem.message + "." + kept;
  }
  for (const [id, element] of elements) {
    element.classList.toggle("bad", !!problem && problem.node === id);
  }
}

// ---- Nodes ----

function addNode(type) {
  const spec = specs[type];
  const base = type.charAt(0).toLowerCase() + type.slice(1);
  let id = base;
  for (let n = 2; nodeById(id); n++) id = base + n;
  const canvas = $("#canvas");
  const node = {
    id,
    type,
    params: {},
    in: spec.inputs.map(() => null),
    x: canvas.scrollLeft + 40 + (graph.nodes.length % 5) * 24,
    y: canvas.scrollTop + 40 + (graph.nodes.length % 5) * 24,
  };
  graph.nodes.push(node);
  render();
  select(id);
  schedulePush();
}

function removeNode(id) {
  graph.nodes = graph.nodes.filter((node) => node.id !== id);
  for (const node of graph.nodes) node.in = node.in.map((source) => (source === id ? null : source));
  if (selected === id) select(null);
  if (picking === id) picking = null;
  render();
  schedulePush();
}

function select(id) {
  selected = id;
  if (picking && picking !== id) picking = null;
  for (const [nodeId, element] of elements) {
    element.classList.toggle("selected", nodeId === id);
    const button = element.querySelector(".pickButton");
    if (button) button.classList.toggle("active", picking === nodeId);
  }
  updateViewer();
}

/** Brings the node elements in line with the graph, keeping those that already exist. */
function render() {
  for (const [id, element] of elements) {
    if (!nodeById(id)) {
      element.remove();
      elements.delete(id);
    }
  }
  for (const node of graph.nodes) {
    node.params = node.params || {};
    node.in = specs[node.type].inputs.map((_, k) => (node.in && node.in[k]) || null);
    if (!elements.has(node.id)) {
      const element = buildNode(node);
      elements.set(node.id, element);
      world.append(element);
    }
  }
  drawWires();
  showProblems();
}

function buildNode(node) {
  const spec = specs[node.type];
  const element = make("div", "node");
  element.style.left = node.x + "px";
  element.style.top = node.y + "px";
  element.title = spec.description;
  element.addEventListener("pointerdown", () => select(node.id));

  const head = make("div", "head");
  head.append(make("b", "", node.id));
  const remove = make("button", "", "×");
  remove.title = "Delete";
  remove.addEventListener("pointerdown", (event) => event.stopPropagation());
  remove.addEventListener("click", () => removeNode(node.id));
  head.append(remove);
  head.addEventListener("pointerdown", (event) => dragNode(event, node, element));
  element.append(head);

  const ports = make("div", "ports");
  const ins = make("div");
  spec.inputs.forEach((input, k) => {
    const port = make("div", "port in");
    port.dataset.node = node.id;
    port.dataset.k = k;
    port.append(make("i"), input.name);
    port.addEventListener("click", () => {
      if (!node.in[k]) return;
      node.in[k] = null;
      drawWires();
      schedulePush();
    });
    ins.append(port);
  });
  ports.append(ins);
  if (!spec.terminal) {
    const out = make("div", "port out");
    out.append(make("span", "outType"), make("i"));
    out.addEventListener("pointerdown", (event) => dragWire(event, node));
    ports.append(out);
  }
  element.append(ports);

  const thumb = make("img", "thumb none");
  thumb.alt = "";
  element.append(thumb);
  pollImage(thumb, () => ($("#thumbs").checked ? node.id : null), 200, 300);

  const params = make("div", "params");
  element.append(params);
  buildParams(node, params);

  const foot = make("div", "foot");
  foot.append(make("span", "value"), make("span", "ms"));
  element.append(foot);
  return element;
}

function dragNode(event, node, element) {
  event.preventDefault();
  const startX = event.clientX - node.x;
  const startY = event.clientY - node.y;
  const move = (e) => {
    node.x = Math.max(0, e.clientX - startX);
    node.y = Math.max(0, e.clientY - startY);
    element.style.left = node.x + "px";
    element.style.top = node.y + "px";
    drawWires();
  };
  const up = () => {
    removeEventListener("pointermove", move);
    removeEventListener("pointerup", up);
    schedulePush();
  };
  addEventListener("pointermove", move);
  addEventListener("pointerup", up);
}

// ---- Parameters ----

function buildParams(node, container) {
  container.replaceChildren();
  const spec = specs[node.type];
  const value = (param) => (param.name in node.params ? node.params[param.name] : param.default);
  const set = (param, v) => {
    node.params[param.name] = v;
    schedulePush();
  };

  for (const param of spec.params) {
    if (param.kind === "TRIPLE") {
      ["1", "2", "3"].forEach((label, c) => {
        const row = make("label", "param");
        const slider = make("input");
        slider.type = "range";
        slider.min = param.min;
        slider.max = param.max;
        slider.value = value(param)[c];
        const shown = make("span", "", String(value(param)[c]));
        slider.addEventListener("input", () => {
          const triple = value(param).slice();
          triple[c] = Number(slider.value);
          shown.textContent = slider.value;
          set(param, triple);
        });
        row.append(make("span", "", param.name + " " + label), slider, shown);
        container.append(row);
      });
      continue;
    }

    const row = make("label", "param");
    row.append(make("span", "", param.name));
    if (param.kind === "CHOICE") {
      const choice = make("select", "wide");
      for (const option of param.choices) choice.append(make("option", "", option));
      choice.value = value(param);
      choice.addEventListener("change", () => set(param, choice.value));
      row.append(choice);
    } else if (param.kind === "TEXT") {
      const text = make("input", "wide");
      text.type = "text";
      text.value = value(param);
      text.addEventListener("input", () => set(param, text.value));
      row.append(text);
    } else {
      // A wide range is given a curved slider, so that small values can still be set.
      const whole = param.kind === "INT";
      const span = param.max - param.min;
      const power = span > 1000 ? 3 : 1;
      const round = (v) => (whole ? Math.round(v) : Number(v.toPrecision(4)));
      const toSlider = (v) => 1000 * Math.pow((v - param.min) / span, 1 / power);
      const fromSlider = (s) => round(param.min + span * Math.pow(s / 1000, power));
      const slider = make("input");
      slider.type = "range";
      slider.min = 0;
      slider.max = 1000;
      slider.value = toSlider(value(param));
      const box = make("input");
      box.type = "number";
      box.min = param.min;
      box.max = param.max;
      box.step = whole ? 1 : "any";
      box.value = value(param);
      slider.addEventListener("input", () => {
        box.value = fromSlider(Number(slider.value));
        set(param, Number(box.value));
      });
      box.addEventListener("input", () => {
        if (box.value === "" || Number.isNaN(Number(box.value))) return;
        slider.value = toSlider(Number(box.value));
        set(param, Number(box.value));
      });
      row.append(slider, box);
    }
    container.append(row);
  }

  if (node.type === "Threshold") {
    const pick = make("button", "pickButton", "Pick from image");
    pick.addEventListener("click", (event) => {
      event.stopPropagation();
      picking = picking === node.id ? null : node.id;
      select(node.id);
    });
    pick.addEventListener("pointerdown", (event) => event.stopPropagation());
    container.append(pick);
  }
}

// ---- Connections ----

function dotCentre(dot) {
  const d = dot.getBoundingClientRect();
  const w = world.getBoundingClientRect();
  return { x: d.left + d.width / 2 - w.left, y: d.top + d.height / 2 - w.top };
}

function curve(a, b) {
  const bend = Math.max(40, Math.abs(b.x - a.x) / 2);
  return `M${a.x},${a.y} C${a.x + bend},${a.y} ${b.x - bend},${b.y} ${b.x},${b.y}`;
}

function outputType(node) {
  const live = status.nodes[node.id];
  return (live && live.type) || specs[node.type].output;
}

function drawWires(loose) {
  wires.replaceChildren();
  const path = (d, colour) => {
    const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
    p.setAttribute("d", d);
    p.setAttribute("stroke", colour);
    wires.append(p);
  };
  for (const node of graph.nodes) {
    const element = elements.get(node.id);
    if (!element) continue;
    element.querySelectorAll(".port.in").forEach((port, k) => {
      const source = node.in[k] && nodeById(node.in[k]);
      const from = source && elements.get(source.id).querySelector(".port.out i");
      port.classList.toggle("on", !!from);
      port.style.color = from ? typeColour(outputType(source)) : "";
      if (from) {
        path(curve(dotCentre(from), dotCentre(port.querySelector("i"))), port.style.color);
      }
    });
  }
  if (loose) path(curve(loose.from, loose.to), css("--accent"));
}

function dragWire(event, source) {
  event.preventDefault();
  event.stopPropagation();
  const from = dotCentre(event.currentTarget.querySelector("i"));
  const point = (e) => {
    const w = world.getBoundingClientRect();
    return { x: e.clientX - w.left, y: e.clientY - w.top };
  };
  const move = (e) => drawWires({ from, to: point(e) });
  const up = (e) => {
    removeEventListener("pointermove", move);
    removeEventListener("pointerup", up);
    const under = document.elementFromPoint(e.clientX, e.clientY);
    const port = under && under.closest(".port.in");
    if (port && port.dataset.node !== source.id) {
      nodeById(port.dataset.node).in[Number(port.dataset.k)] = source.id;
      schedulePush();
    }
    drawWires();
  };
  addEventListener("pointermove", move);
  addEventListener("pointerup", up);
}

// ---- Pictures ----

/**
 * Keeps an image showing the latest picture of a node, asking for the next one only once the last
 * has arrived. A node is watched by the robot only while its picture is being asked for.
 */
function pollImage(image, nodeId, width, gap) {
  const next = () => {
    if (!image.isConnected) return;
    const id = nodeId();
    if (!id) {
      image.classList.add("none");
      setTimeout(next, 500);
      return;
    }
    image.src = `/api/frame?node=${encodeURIComponent(id)}&w=${width}&t=${Date.now()}`;
  };
  image.addEventListener("load", () => {
    image.classList.remove("none");
    setTimeout(next, gap);
  });
  image.addEventListener("error", () => {
    image.classList.add("none");
    setTimeout(next, 500);
  });
  setTimeout(next, 0);
}

/** The node whose picture the viewer shows: while picking, the input of the node being tuned. */
function viewedNode() {
  if (picking) {
    const node = nodeById(picking);
    return node ? node.in[0] : null;
  }
  return selected;
}

function updateViewer() {
  const id = viewedNode();
  viewer.classList.toggle("pick", !!picking);
  $("#viewerTitle").textContent = id || "Select a node";
  $("#hint").textContent = picking
    ? "Click the colour to keep. Shift-click to widen the range to include another."
    : "";
}

viewer.addEventListener("click", async (event) => {
  const node = picking && nodeById(picking);
  if (!node || !node.in[0]) return;
  const box = viewer.getBoundingClientRect();
  const x = (event.clientX - box.left) / box.width;
  const y = (event.clientY - box.top) / box.height;
  let sample;
  try {
    sample = await api(`/api/sample?node=${encodeURIComponent(node.in[0])}&x=${x}&y=${y}&r=5`);
  } catch (error) {
    $("#hint").textContent = error.message;
    return;
  }
  const margin = [8, 40, 40];
  const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
  let min = [0, 1, 2].map((c) => clamp((sample.min[c] ?? sample.min[0]) - margin[c]));
  let max = [0, 1, 2].map((c) => clamp((sample.max[c] ?? sample.max[0]) + margin[c]));
  if (event.shiftKey && node.params.min && node.params.max) {
    min = min.map((v, c) => Math.min(v, node.params.min[c]));
    max = max.map((v, c) => Math.max(v, node.params.max[c]));
  }
  node.params.min = min;
  node.params.max = max;
  buildParams(node, elements.get(node.id).querySelector(".params"));
  select(node.id);
  schedulePush();
});

// ---- Status ----

async function pollStatus() {
  try {
    status = await api("/api/status");
    $("#stats").textContent =
      `${status.width}×${status.height}  ${status.fps.toFixed(1)} fps  ` +
      `${Object.values(status.nodes)
        .reduce((sum, node) => sum + node.ms, 0)
        .toFixed(1)} ms per frame`;
    $("#file").textContent = status.file || "";
    $("#save").hidden = !status.file;
  } catch (error) {
    $("#stats").textContent = "Not connected";
    status = { nodes: {} };
  }
  for (const node of graph.nodes) {
    const element = elements.get(node.id);
    const live = status.nodes[node.id];
    if (!element) continue;
    element.classList.toggle("inactive", !live || !live.type);
    element.querySelector(".ms").textContent = live && live.type ? live.ms.toFixed(2) + " ms" : "";
    element.querySelector(".value").textContent =
      live && live.value !== undefined ? live.value.toFixed(1) : "";
    const outType = element.querySelector(".outType");
    if (outType) {
      const type = outputType(node);
      outType.textContent = type ? type.toLowerCase() : "";
      outType.parentElement.style.color = typeColour(type);
      outType.parentElement.classList.toggle("on", !!type);
    }
  }
  drawWires();
  showProblems();
  showResults();
  setTimeout(pollStatus, 400);
}

function showResults() {
  const results = $("#results");
  results.replaceChildren();
  const row = (name, text) => {
    const line = make("div");
    line.append(make("b", "", name), " " + text);
    results.append(line);
  };
  for (const [name, value] of Object.entries(status.numbers || {})) {
    row(name, value === null ? "none" : value.toFixed(2));
  }
  for (const [name, list] of Object.entries(status.detections || {})) {
    if (list.length === 0) row(name, "nothing found");
    list.slice(0, 5).forEach((d, i) => {
      row(
        list.length > 1 ? `${name}[${i}]` : name,
        `x ${d.x.toFixed(0)}  y ${d.y.toFixed(0)}  ${d.width.toFixed(0)}×${d.height.toFixed(0)}  ` +
          `${d.angle.toFixed(0)}°  area ${d.area.toFixed(0)}`,
      );
    });
  }
  if (!results.children.length) results.append(make("div", "", "Add an Output node."));
}

// ---- Toolbar ----

$("#save").addEventListener("click", async () => {
  clearTimeout(pushTimer);
  await push();
  try {
    await api("/api/save", { method: "POST" });
    $("#save").textContent = "Saved";
  } catch (error) {
    $("#save").textContent = "Not saved";
    pushError = pushError || { message: error.message };
    showProblems();
  }
  setTimeout(() => ($("#save").textContent = "Save"), 1500);
});

$("#open").addEventListener("click", () => $("#openFile").click());
$("#openFile").addEventListener("change", async (event) => {
  const file = event.target.files[0];
  event.target.value = "";
  if (!file) return;
  try {
    const opened = JSON.parse(await file.text());
    const unknown = opened.nodes.find((node) => !specs[node.type]);
    if (unknown) throw new Error(`Unknown node type "${unknown.type}"`);
    load(opened);
    schedulePush();
  } catch (error) {
    pushError = { message: "Cannot open " + file.name + ": " + error.message };
    showProblems();
  }
});

addEventListener("keydown", (event) => {
  const typing = /^(INPUT|SELECT|TEXTAREA)$/.test(event.target.tagName);
  if ((event.key === "Delete" || event.key === "Backspace") && selected && !typing) {
    removeNode(selected);
  }
});

function load(next) {
  for (const element of elements.values()) element.remove();
  elements.clear();
  graph = { version: 1, nodes: next.nodes || [] };
  select(null);
  render();
}

async function start() {
  const categories = new Map();
  for (const spec of await api("/api/nodes")) {
    specs[spec.type] = spec;
    if (!categories.has(spec.category)) categories.set(spec.category, []);
    categories.get(spec.category).push(spec);
  }
  for (const [category, list] of categories) {
    $("#palette").append(make("h2", "", category));
    for (const spec of list) {
      const button = make("button", "", spec.type);
      button.title = spec.description;
      button.addEventListener("click", () => addNode(spec.type));
      $("#palette").append(button);
    }
  }
  load(await api("/api/graph"));
  pollImage(viewer, viewedNode, 800, 50);
  pollStatus();
}

start();
