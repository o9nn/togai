"""
Tests for Phase 4: Cognitive Mesh API

Tests the REST API endpoints, agent registration, and sensor/effector management.
"""

import unittest
from python.helpers.phase4.api.cognitive_mesh_api import (
    CognitiveMeshAPI,
    MeshStatus,
    AgentRegistration,
    SystemHealth,
)


class TestCognitiveMeshAPI(unittest.TestCase):
    """Test cases for Cognitive Mesh API."""
    
    def setUp(self):
        """Set up test fixtures."""
        self.api = CognitiveMeshAPI(port=8081, host="localhost")
    
    def test_initialization(self):
        """Test API initialization."""
        self.assertIsNotNone(self.api)
        self.assertEqual(self.api.port, 8081)
        self.assertEqual(self.api.host, "localhost")
        self.assertFalse(self.api.running)
    
    def test_get_mesh_status(self):
        """Test getting mesh status."""
        status = self.api.get_mesh_status()
        
        self.assertIsInstance(status, MeshStatus)
        self.assertEqual(status.agents, 0.0)
        self.assertEqual(status.sensors, 0.0)
        self.assertEqual(status.effectors, 0.0)
        self.assertEqual(status.state, 0.0)
        self.assertEqual(status.system_health, SystemHealth.HEALTHY.value)
    
    def test_register_agent(self):
        """Test agent registration."""
        registration = self.api.register_agent(
            client_name="test_agent",
            capabilities=["sensing", "acting"],
            agent_type="generic",
        )
        
        self.assertIsInstance(registration, AgentRegistration)
        self.assertTrue(registration.success)
        self.assertIsNotNone(registration.agent_id)
        self.assertIn("agent-", registration.agent_id)
        self.assertIn("status", registration.api_endpoints)
        self.assertIn("sensors", registration.api_endpoints)
        self.assertIn("effectors", registration.api_endpoints)
    
    def test_submit_sensor_data(self):
        """Test sensor data submission."""
        # Register agent first
        registration = self.api.register_agent("test_agent", ["test"], "generic")
        
        # Submit sensor data
        result = self.api.submit_sensor_data(
            agent_id=registration.agent_id,
            sensor_type="temperature",
            sensor_data={"value": 22.5},
        )
        
        self.assertTrue(result["success"])
        self.assertEqual(result["agent_id"], registration.agent_id)
        self.assertEqual(result["sensor_type"], "temperature")
        self.assertTrue(result["processed"])
    
    def test_get_effector_actions(self):
        """Test getting effector actions."""
        # Register agent first
        registration = self.api.register_agent("test_agent", ["test"], "unity3d")
        
        # Get effector actions
        result = self.api.get_effector_actions(registration.agent_id)
        
        self.assertTrue(result["success"])
        self.assertEqual(result["agent_id"], registration.agent_id)
        self.assertIn("available_actions", result)
        self.assertIn("recommendations", result)
        self.assertIsInstance(result["available_actions"], list)
    
    def test_mesh_status_with_agents(self):
        """Test mesh status with registered agents."""
        # Register multiple agents
        for i in range(3):
            self.api.register_agent(f"agent_{i}", ["test"], "generic")
        
        status = self.api.get_mesh_status()
        
        self.assertEqual(status.agents, 3.0)
        self.assertGreaterEqual(status.state, 0.0)
        self.assertLessEqual(status.state, 1.0)


class TestMeshStatus(unittest.TestCase):
    """Test cases for MeshStatus data class."""
    
    def test_mesh_status_creation(self):
        """Test MeshStatus creation."""
        status = MeshStatus(
            agents=5.0,
            sensors=15.0,
            effectors=10.0,
            state=0.75,
        )
        
        self.assertEqual(status.agents, 5.0)
        self.assertEqual(status.sensors, 15.0)
        self.assertEqual(status.effectors, 10.0)
        self.assertEqual(status.state, 0.75)
        self.assertIsNotNone(status.mesh_tensor)
    
    def test_mesh_status_defaults(self):
        """Test MeshStatus default values."""
        status = MeshStatus()
        
        self.assertEqual(status.agents, 0.0)
        self.assertEqual(status.system_health, "HEALTHY")
        self.assertEqual(status.average_attention, 0.0)


if __name__ == "__main__":
    unittest.main()
