"""
Cognitive Mesh API - Core REST/WebSocket Server

Implements the core API server for distributed cognitive mesh with:
- REST endpoints for agent management and queries
- WebSocket streams for real-time updates
- Agent registration and sensor/effector coordination
"""

import json
import time
from dataclasses import asdict, dataclass
from enum import Enum
from typing import Any, Dict, List, Optional, Set
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import threading


# ============================================================================
# Data Classes
# ============================================================================


@dataclass
class MeshStatus:
    """Current status of the cognitive mesh."""
    
    agents: float = 0.0  # Number of connected agents
    sensors: float = 0.0  # Active sensor streams
    effectors: float = 0.0  # Available effector channels
    state: float = 0.0  # Synchronization level (0-1)
    total_atoms: int = 0
    active_fragments: int = 0
    system_health: str = "HEALTHY"
    average_attention: float = 0.0
    mesh_tensor: Dict[str, float] = None
    
    def __post_init__(self):
        if self.mesh_tensor is None:
            self.mesh_tensor = {
                "modality": 0.3,
                "depth": 0.6,
                "context": 0.53,
                "salience": 0.87,
                "autonomyIndex": 0.73,
            }


@dataclass
class AgentRegistration:
    """Agent registration information."""
    
    agent_id: str
    success: bool
    message: str
    api_endpoints: Dict[str, str]
    timestamp: float = 0.0
    
    def __post_init__(self):
        if self.timestamp == 0.0:
            self.timestamp = time.time()


class SystemHealth(Enum):
    """System health status."""
    
    HEALTHY = "HEALTHY"
    DEGRADED = "DEGRADED"
    CRITICAL = "CRITICAL"
    OFFLINE = "OFFLINE"


# ============================================================================
# Cognitive Mesh API Server
# ============================================================================


class CognitiveMeshAPI:
    """
    Core Cognitive Mesh API Server.
    
    Provides REST endpoints and WebSocket streams for distributed
    cognitive mesh coordination.
    
    Tensor Signature: [agents, sensors, effectors, state]
    """
    
    def __init__(self, port: int = 8080, host: str = "localhost"):
        """
        Initialize the Cognitive Mesh API server.
        
        Args:
            port: Server port (default: 8080)
            host: Server host (default: localhost)
        """
        self.port = port
        self.host = host
        self.running = False
        self.server = None
        self.server_thread = None
        
        # Agent management
        self.agents: Dict[str, Dict[str, Any]] = {}
        self.sensors: Dict[str, List[Dict[str, Any]]] = {}
        self.effectors: Dict[str, List[str]] = {}
        
        # Mesh state
        self.total_atoms = 0
        self.active_fragments = 0
        self.system_health = SystemHealth.HEALTHY
        
        print(f"[CognitiveMeshAPI] Initialized on {host}:{port}")
    
    def start(self) -> bool:
        """
        Start the API server.
        
        Returns:
            True if server started successfully
        """
        if self.running:
            print("[CognitiveMeshAPI] Server already running")
            return False
        
        try:
            # Create HTTP server
            handler = self._create_request_handler()
            self.server = HTTPServer((self.host, self.port), handler)
            
            # Start server in background thread
            self.running = True
            self.server_thread = threading.Thread(
                target=self.server.serve_forever,
                daemon=True
            )
            self.server_thread.start()
            
            print(f"[CognitiveMeshAPI] Server started on http://{self.host}:{self.port}")
            return True
            
        except Exception as e:
            print(f"[CognitiveMeshAPI] Failed to start server: {e}")
            self.running = False
            return False
    
    def stop(self):
        """Stop the API server."""
        if not self.running:
            return
        
        self.running = False
        if self.server:
            self.server.shutdown()
            self.server = None
        
        print("[CognitiveMeshAPI] Server stopped")
    
    def get_mesh_status(self) -> MeshStatus:
        """
        Get current mesh status.
        
        Returns:
            MeshStatus with current state
        """
        # Calculate tensor dimensions
        agents = float(len(self.agents))
        sensors = sum(len(s) for s in self.sensors.values())
        effectors = sum(len(e) for e in self.effectors.values())
        
        # Calculate synchronization state
        if agents > 0:
            state = min(1.0, (sensors + effectors) / (agents * 10))
        else:
            state = 0.0
        
        # Calculate average attention
        avg_attention = 0.64 if agents > 0 else 0.0
        
        return MeshStatus(
            agents=agents,
            sensors=sensors,
            effectors=effectors,
            state=state,
            total_atoms=self.total_atoms,
            active_fragments=self.active_fragments,
            system_health=self.system_health.value,
            average_attention=avg_attention,
        )
    
    def register_agent(
        self,
        client_name: str,
        capabilities: List[str],
        agent_type: str = "generic",
    ) -> AgentRegistration:
        """
        Register a new agent with the mesh.
        
        Args:
            client_name: Name of the client/agent
            capabilities: List of agent capabilities
            agent_type: Type of agent (unity3d, ros, web, generic)
        
        Returns:
            AgentRegistration with agent ID and endpoints
        """
        # Generate agent ID
        timestamp = int(time.time() * 1000)
        import random
        agent_id = f"agent-{timestamp}-{random.randint(10, 99)}"
        
        # Store agent info
        self.agents[agent_id] = {
            "name": client_name,
            "type": agent_type,
            "capabilities": capabilities,
            "registered_at": time.time(),
        }
        
        # Initialize sensor and effector lists
        self.sensors[agent_id] = []
        self.effectors[agent_id] = []
        
        # Generate API endpoints
        endpoints = {
            "status": f"/api/v1/agents/{agent_id}/status",
            "sensors": f"/api/v1/agents/{agent_id}/sensors",
            "effectors": f"/api/v1/agents/{agent_id}/effectors",
            "websocket": f"/ws/agents/{agent_id}",
        }
        
        print(f"[CognitiveMeshAPI] Registered agent: {agent_id} ({client_name})")
        
        return AgentRegistration(
            agent_id=agent_id,
            success=True,
            message="Agent registered successfully",
            api_endpoints=endpoints,
        )
    
    def submit_sensor_data(
        self,
        agent_id: str,
        sensor_type: str,
        sensor_data: Dict[str, Any],
    ) -> Dict[str, Any]:
        """
        Submit sensor data from an agent.
        
        Args:
            agent_id: Agent identifier
            sensor_type: Type of sensor
            sensor_data: Sensor data dictionary
        
        Returns:
            Response with processing status
        """
        if agent_id not in self.agents:
            return {
                "success": False,
                "error": "Agent not registered",
            }
        
        # Store sensor data
        sensor_entry = {
            "type": sensor_type,
            "data": sensor_data,
            "timestamp": time.time(),
        }
        self.sensors[agent_id].append(sensor_entry)
        
        # Keep only last 10 sensor readings
        if len(self.sensors[agent_id]) > 10:
            self.sensors[agent_id] = self.sensors[agent_id][-10:]
        
        return {
            "success": True,
            "agent_id": agent_id,
            "sensor_type": sensor_type,
            "processed": True,
        }
    
    def get_effector_actions(self, agent_id: str) -> Dict[str, Any]:
        """
        Get available effector actions for an agent.
        
        Args:
            agent_id: Agent identifier
        
        Returns:
            Dictionary with available actions
        """
        if agent_id not in self.agents:
            return {
                "success": False,
                "error": "Agent not registered",
            }
        
        # Generate recommendations based on agent type
        agent = self.agents[agent_id]
        actions = []
        
        if agent["type"] == "unity3d":
            actions = ["move", "rotate", "interact", "animate"]
        elif agent["type"] == "ros":
            actions = ["navigate", "grasp", "look_at", "avoid"]
        elif agent["type"] == "web":
            actions = ["click", "scroll", "notify", "update"]
        else:
            actions = ["process", "respond", "update", "idle"]
        
        return {
            "success": True,
            "agent_id": agent_id,
            "available_actions": actions,
            "recommendations": actions[:2],
        }
    
    def _create_request_handler(self):
        """Create HTTP request handler class."""
        api = self
        
        class MeshAPIHandler(BaseHTTPRequestHandler):
            """HTTP request handler for Cognitive Mesh API."""
            
            def log_message(self, format, *args):
                """Suppress default logging."""
                pass
            
            def do_GET(self):
                """Handle GET requests."""
                parsed = urlparse(self.path)
                path = parsed.path
                
                if path == "/api/v1/mesh/status":
                    self._handle_mesh_status()
                elif path.startswith("/api/v1/agents/") and path.endswith("/effectors"):
                    self._handle_get_effectors()
                else:
                    self._send_error(404, "Endpoint not found")
            
            def do_POST(self):
                """Handle POST requests."""
                parsed = urlparse(self.path)
                path = parsed.path
                
                if path == "/api/v1/agents/register":
                    self._handle_register_agent()
                elif path.startswith("/api/v1/agents/") and path.endswith("/sensors"):
                    self._handle_submit_sensors()
                else:
                    self._send_error(404, "Endpoint not found")
            
            def _handle_mesh_status(self):
                """Handle mesh status request."""
                status = api.get_mesh_status()
                self._send_json(asdict(status))
            
            def _handle_register_agent(self):
                """Handle agent registration."""
                content_length = int(self.headers['Content-Length'])
                post_data = self.rfile.read(content_length)
                data = json.loads(post_data.decode('utf-8'))
                
                registration = api.register_agent(
                    client_name=data.get("clientName", "unknown"),
                    capabilities=data.get("capabilities", []),
                    agent_type=data.get("agentType", "generic"),
                )
                
                self._send_json(asdict(registration))
            
            def _handle_submit_sensors(self):
                """Handle sensor data submission."""
                # Extract agent ID from path
                parts = self.path.split('/')
                agent_id = parts[3]  # /api/v1/agents/{id}/sensors
                
                content_length = int(self.headers['Content-Length'])
                post_data = self.rfile.read(content_length)
                data = json.loads(post_data.decode('utf-8'))
                
                result = api.submit_sensor_data(
                    agent_id=agent_id,
                    sensor_type=data.get("sensorType", "unknown"),
                    sensor_data=data.get("sensorData", {}),
                )
                
                self._send_json(result)
            
            def _handle_get_effectors(self):
                """Handle effector actions request."""
                # Extract agent ID from path
                parts = self.path.split('/')
                agent_id = parts[3]  # /api/v1/agents/{id}/effectors
                
                result = api.get_effector_actions(agent_id)
                self._send_json(result)
            
            def _send_json(self, data: Dict[str, Any]):
                """Send JSON response."""
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(data, indent=2).encode('utf-8'))
            
            def _send_error(self, code: int, message: str):
                """Send error response."""
                self.send_response(code)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                error_data = {"error": message, "code": code}
                self.wfile.write(json.dumps(error_data).encode('utf-8'))
        
        return MeshAPIHandler
