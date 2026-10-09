"""
Tests for Phase 4: Embodiment Bindings

Tests Unity3D, ROS, and Web Agent bindings.
"""

import unittest
from python.helpers.phase4.api.cognitive_mesh_api import CognitiveMeshAPI
from python.helpers.phase4.embodiment.unity3d_binding import (
    Unity3DBinding,
    UnityTransform,
    Vector3,
    UnityEventType,
)
from python.helpers.phase4.embodiment.ros_binding import (
    ROSBinding,
    ROSSensorType,
)
from python.helpers.phase4.embodiment.web_agent_binding import (
    WebAgentBinding,
    DOMInteraction,
    DOMInteractionType,
    UserBehaviorData,
)


class TestUnity3DBinding(unittest.TestCase):
    """Test cases for Unity3D binding."""
    
    def setUp(self):
        """Set up test fixtures."""
        self.api = CognitiveMeshAPI(port=8082)
        self.unity = Unity3DBinding(self.api)
    
    def test_initialization(self):
        """Test Unity3D binding initialization."""
        self.assertIsNotNone(self.unity)
        self.assertIsNotNone(self.unity.mesh_api)
    
    def test_connect_unity_client(self):
        """Test Unity client connection."""
        connection = self.unity.connect_unity_client(
            "test_client",
            ["Transform", "Physics"],
            "TestScene",
        )
        
        self.assertTrue(connection.success)
        self.assertIsNotNone(connection.agent_id)
        self.assertEqual(connection.client_name, "test_client")
        self.assertIn("Transform", connection.capabilities)
    
    def test_update_game_object_transform(self):
        """Test GameObject transform update."""
        connection = self.unity.connect_unity_client("test", ["Transform"], "Scene")
        
        transform = UnityTransform(
            Vector3(1.0, 2.0, 3.0),
            Vector3.zero(),
            Vector3.one(),
        )
        
        result = self.unity.update_game_object_transform(
            connection.agent_id,
            "TestObject",
            transform,
        )
        
        self.assertTrue(result["success"])
        self.assertEqual(result["game_object_id"], "TestObject")
        self.assertTrue(result["transform_updated"])
    
    def test_handle_unity_event(self):
        """Test Unity event handling."""
        connection = self.unity.connect_unity_client("test", ["Physics"], "Scene")
        
        result = self.unity.handle_unity_event(
            connection.agent_id,
            UnityEventType.COLLISION,
            {"collider": "Wall", "force": 10.0},
        )
        
        self.assertTrue(result["success"])
        self.assertEqual(result["event_type"], "COLLISION")
        self.assertIn("recommendations", result)
        self.assertIn("cognitive_response", result)


class TestROSBinding(unittest.TestCase):
    """Test cases for ROS binding."""
    
    def setUp(self):
        """Set up test fixtures."""
        self.api = CognitiveMeshAPI(port=8083)
        self.ros = ROSBinding(self.api)
    
    def test_initialization(self):
        """Test ROS binding initialization."""
        self.assertIsNotNone(self.ros)
        self.assertIsNotNone(self.ros.mesh_api)
    
    def test_connect_ros_node(self):
        """Test ROS node connection."""
        connection = self.ros.connect_ros_node(
            "test_node",
            ["navigation", "perception"],
            "ROS2",
        )
        
        self.assertTrue(connection.success)
        self.assertIsNotNone(connection.agent_id)
        self.assertEqual(connection.node_name, "test_node")
        self.assertEqual(connection.ros_version, "ROS2")
    
    def test_publish_to_topic(self):
        """Test ROS topic publishing."""
        connection = self.ros.connect_ros_node("test", ["nav"], "ROS2")
        
        result = self.ros.publish_to_topic(
            connection.agent_id,
            "/cmd_vel",
            "geometry_msgs/Twist",
            {"linear": {"x": 0.5}},
        )
        
        self.assertTrue(result["success"])
        self.assertEqual(result["topic_name"], "/cmd_vel")
        self.assertIn("cognitive_feedback", result)
    
    def test_process_sensor_data(self):
        """Test ROS sensor data processing."""
        connection = self.ros.connect_ros_node("test", ["perception"], "ROS2")
        
        result = self.ros.process_sensor_data(
            connection.agent_id,
            ROSSensorType.LASER_SCAN,
            {"ranges": [2.5, 1.8, 0.5]},
        )
        
        self.assertTrue(result["success"])
        self.assertEqual(result["sensor_type"], "LASER_SCAN")
        self.assertIn("cognitive_insights", result)
    
    def test_navigation_recommendations(self):
        """Test navigation recommendations."""
        connection = self.ros.connect_ros_node("test", ["nav"], "ROS2")
        
        result = self.ros.get_navigation_recommendations(
            connection.agent_id,
            {"x": 0.0, "y": 0.0},
            {"x": 5.0, "y": 3.0},
        )
        
        self.assertTrue(result["success"])
        self.assertIn("distance_to_goal", result)
        self.assertIn("recommendations", result)


class TestWebAgentBinding(unittest.TestCase):
    """Test cases for Web Agent binding."""
    
    def setUp(self):
        """Set up test fixtures."""
        self.api = CognitiveMeshAPI(port=8084)
        self.web = WebAgentBinding(self.api)
    
    def test_initialization(self):
        """Test Web Agent binding initialization."""
        self.assertIsNotNone(self.web)
        self.assertIsNotNone(self.web.mesh_api)
    
    def test_connect_web_client(self):
        """Test web client connection."""
        connection = self.web.connect_web_client(
            "test_client",
            ["DOM", "Storage"],
            "TestAgent/1.0",
        )
        
        self.assertTrue(connection.success)
        self.assertIsNotNone(connection.agent_id)
        self.assertEqual(connection.client_id, "test_client")
    
    def test_handle_dom_interaction(self):
        """Test DOM interaction handling."""
        connection = self.web.connect_web_client("test", ["DOM"], "Test/1.0")
        
        interaction = DOMInteraction(
            type=DOMInteractionType.CLICK,
            element_id="button1",
            element_data={"tagName": "button"},
        )
        
        result = self.web.handle_dom_interaction(
            connection.agent_id,
            interaction,
        )
        
        self.assertTrue(result["success"])
        self.assertEqual(result["interaction_type"], "CLICK")
        self.assertIn("cognitive_insights", result)
    
    def test_process_user_behavior(self):
        """Test user behavior processing."""
        connection = self.web.connect_web_client("test", ["DOM"], "Test/1.0")
        
        behavior = UserBehaviorData(
            actions=["click", "scroll"],
            session_duration=300000,
            page_views=3,
            engagement_score=0.7,
        )
        
        result = self.web.process_user_behavior(
            connection.agent_id,
            behavior,
        )
        
        self.assertTrue(result["success"])
        self.assertIn("behavior_analysis", result)
        self.assertIn("engagement_level", result)
    
    def test_generate_javascript_sdk(self):
        """Test JavaScript SDK generation."""
        connection = self.web.connect_web_client("test", ["DOM"], "Test/1.0")
        
        sdk = self.web.generate_javascript_sdk(connection.agent_id)
        
        self.assertIsInstance(sdk, str)
        self.assertIn("CognitiveMeshWebSDK", sdk)
        self.assertIn(connection.agent_id, sdk)


class TestVector3(unittest.TestCase):
    """Test cases for Vector3 data class."""
    
    def test_vector3_creation(self):
        """Test Vector3 creation."""
        v = Vector3(1.0, 2.0, 3.0)
        self.assertEqual(v.x, 1.0)
        self.assertEqual(v.y, 2.0)
        self.assertEqual(v.z, 3.0)
    
    def test_vector3_zero(self):
        """Test Vector3.zero()."""
        v = Vector3.zero()
        self.assertEqual(v.x, 0.0)
        self.assertEqual(v.y, 0.0)
        self.assertEqual(v.z, 0.0)
    
    def test_vector3_one(self):
        """Test Vector3.one()."""
        v = Vector3.one()
        self.assertEqual(v.x, 1.0)
        self.assertEqual(v.y, 1.0)
        self.assertEqual(v.z, 1.0)


if __name__ == "__main__":
    unittest.main()
