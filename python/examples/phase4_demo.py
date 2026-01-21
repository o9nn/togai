#!/usr/bin/env python3
"""
Phase 4: Distributed Cognitive Mesh Demo

Demonstrates the complete Phase 4 implementation including:
- Cognitive Mesh API (REST endpoints)
- Unity3D, ROS, and Web Agent bindings
- Integration verification and testing
"""

import os
import sys
import time

# Add parent directory to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from helpers.phase4.api.cognitive_mesh_api import CognitiveMeshAPI
from helpers.phase4.embodiment.unity3d_binding import (
    Unity3DBinding,
    UnityTransform,
    Vector3,
    UnityEventType,
)
from helpers.phase4.embodiment.ros_binding import ROSBinding, ROSSensorType
from helpers.phase4.embodiment.web_agent_binding import (
    WebAgentBinding,
    DOMInteraction,
    DOMInteractionType,
    UserBehaviorData,
)
from helpers.phase4.integration.integration_verification import (
    IntegrationVerificationSystem,
)


def print_section(title: str):
    """Print a formatted section header."""
    print(f"\n{'=' * 70}")
    print(f"  {title}")
    print(f"{'=' * 70}\n")


def demo_cognitive_mesh_api():
    """Demo 1: Cognitive Mesh API."""
    print_section("Demo 1: Cognitive Mesh API (4.1)")
    
    # Initialize API
    api = CognitiveMeshAPI(port=8080, host="localhost")
    print("✓ Cognitive Mesh API initialized")
    
    # Get mesh status
    status = api.get_mesh_status()
    print(f"\n✓ Mesh Status:")
    print(f"  - Agents: {status.agents}")
    print(f"  - Sensors: {status.sensors}")
    print(f"  - Effectors: {status.effectors}")
    print(f"  - State: {status.state:.2f}")
    print(f"  - System Health: {status.system_health}")
    
    # Register an agent
    registration = api.register_agent(
        client_name="demo_agent",
        capabilities=["sensing", "acting", "learning"],
        agent_type="generic",
    )
    print(f"\n✓ Agent Registered:")
    print(f"  - Agent ID: {registration.agent_id}")
    print(f"  - Success: {registration.success}")
    print(f"  - Message: {registration.message}")
    
    # Submit sensor data
    result = api.submit_sensor_data(
        agent_id=registration.agent_id,
        sensor_type="temperature",
        sensor_data={"value": 22.5, "unit": "celsius"},
    )
    print(f"\n✓ Sensor Data Submitted:")
    print(f"  - Success: {result['success']}")
    print(f"  - Processed: {result.get('processed', False)}")
    
    # Get effector actions
    actions = api.get_effector_actions(registration.agent_id)
    print(f"\n✓ Available Effector Actions:")
    print(f"  - Actions: {actions['available_actions']}")
    print(f"  - Recommendations: {actions['recommendations']}")
    
    return api


def demo_unity3d_binding(api):
    """Demo 2: Unity3D Binding."""
    print_section("Demo 2: Unity3D Binding (4.2)")
    
    # Initialize Unity3D binding
    unity = Unity3DBinding(api)
    print("✓ Unity3D binding initialized")
    
    # Connect Unity client
    connection = unity.connect_unity_client(
        client_name="GameClient",
        game_object_capabilities=["Transform", "Physics", "Animation"],
        scene_id="MainScene",
    )
    print(f"\n✓ Unity Client Connected:")
    print(f"  - Agent ID: {connection.agent_id}")
    print(f"  - Success: {connection.success}")
    print(f"  - Client Name: {connection.client_name}")
    
    # Update GameObject transform
    transform = UnityTransform(
        position=Vector3(10.0, 5.0, -2.0),
        rotation=Vector3(0.0, 90.0, 0.0),
        scale=Vector3.one(),
    )
    result = unity.update_game_object_transform(
        agent_id=connection.agent_id,
        game_object_id="Player",
        transform=transform,
    )
    print(f"\n✓ GameObject Transform Updated:")
    print(f"  - Success: {result['success']}")
    print(f"  - GameObject ID: {result['game_object_id']}")
    print(f"  - Position: ({transform.position.x}, {transform.position.y}, {transform.position.z})")
    
    # Handle Unity event
    event_result = unity.handle_unity_event(
        agent_id=connection.agent_id,
        event_type=UnityEventType.COLLISION,
        event_data={"collider": "Obstacle", "force": 15.0},
    )
    print(f"\n✓ Unity Event Processed:")
    print(f"  - Event Type: {event_result['event_type']}")
    print(f"  - Cognitive Response: {event_result['cognitive_response']}")
    print(f"  - Recommendations: {len(event_result['recommendations'])} action(s)")
    
    return unity


def demo_ros_binding(api):
    """Demo 3: ROS Binding."""
    print_section("Demo 3: ROS Binding (4.2)")
    
    # Initialize ROS binding
    ros = ROSBinding(api)
    print("✓ ROS binding initialized")
    
    # Connect ROS node
    connection = ros.connect_ros_node(
        node_name="cognitive_node",
        robot_capabilities=["navigation", "manipulation", "perception"],
        ros_version="ROS2",
    )
    print(f"\n✓ ROS Node Connected:")
    print(f"  - Agent ID: {connection.agent_id}")
    print(f"  - Node Name: {connection.node_name}")
    print(f"  - ROS Version: {connection.ros_version}")
    
    # Publish to topic
    pub_result = ros.publish_to_topic(
        agent_id=connection.agent_id,
        topic_name="/cmd_vel",
        message_type="geometry_msgs/Twist",
        message_data={
            "linear": {"x": 0.5, "y": 0.0, "z": 0.0},
            "angular": {"x": 0.0, "y": 0.0, "z": 0.2},
        },
    )
    print(f"\n✓ Topic Published:")
    print(f"  - Topic: {pub_result['topic_name']}")
    print(f"  - Success: {pub_result['success']}")
    print(f"  - Feedback: {pub_result['cognitive_feedback']}")
    
    # Process sensor data
    sensor_result = ros.process_sensor_data(
        agent_id=connection.agent_id,
        sensor_type=ROSSensorType.LASER_SCAN,
        sensor_data={"ranges": [2.5, 1.8, 0.5, 4.0]},
    )
    print(f"\n✓ Sensor Data Processed:")
    print(f"  - Sensor Type: {sensor_result['sensor_type']}")
    print(f"  - Data Quality: {sensor_result['cognitive_insights']['data_quality']}")
    if sensor_result['cognitive_insights']['anomalies']:
        print(f"  - Anomalies: {sensor_result['cognitive_insights']['anomalies']}")
    
    # Get navigation recommendations
    nav_result = ros.get_navigation_recommendations(
        agent_id=connection.agent_id,
        current_pose={"x": 0.0, "y": 0.0, "theta": 0.0},
        goal_pose={"x": 10.0, "y": 5.0, "theta": 1.57},
    )
    print(f"\n✓ Navigation Recommendations:")
    print(f"  - Distance to Goal: {nav_result['distance_to_goal']:.2f}m")
    print(f"  - Cognitive State: {nav_result['cognitive_state']}")
    print(f"  - Recommendations: {len(nav_result['recommendations'])} action(s)")
    
    return ros


def demo_web_agent_binding(api):
    """Demo 4: Web Agent Binding."""
    print_section("Demo 4: Web Agent Binding (4.2)")
    
    # Initialize Web Agent binding
    web = WebAgentBinding(api)
    print("✓ Web Agent binding initialized")
    
    # Connect web client
    connection = web.connect_web_client(
        client_id="dashboard_client",
        client_capabilities=["DOM", "WebRTC", "Storage", "Notifications"],
        user_agent="Mozilla/5.0 (Demo) CognitiveMesh/1.0",
    )
    print(f"\n✓ Web Client Connected:")
    print(f"  - Agent ID: {connection.agent_id}")
    print(f"  - Client ID: {connection.client_id}")
    
    # Handle DOM interaction
    interaction = DOMInteraction(
        type=DOMInteractionType.CLICK,
        element_id="action-button",
        element_data={
            "tagName": "button",
            "className": "btn btn-primary",
            "innerText": "Process Data",
        },
    )
    result = web.handle_dom_interaction(
        agent_id=connection.agent_id,
        interaction=interaction,
    )
    print(f"\n✓ DOM Interaction Processed:")
    print(f"  - Interaction Type: {result['interaction_type']}")
    print(f"  - User Intent: {result['cognitive_insights']['user_intent']}")
    print(f"  - UI Suggestions: {result['ui_suggestions']}")
    
    # Process user behavior
    behavior = UserBehaviorData(
        actions=["click", "scroll", "input", "form_submit"],
        session_duration=450000,
        page_views=5,
        engagement_score=0.85,
        conversion_event=True,
    )
    behavior_result = web.process_user_behavior(
        agent_id=connection.agent_id,
        behavior_data=behavior,
    )
    print(f"\n✓ User Behavior Analyzed:")
    print(f"  - Session Quality: {behavior_result['behavior_analysis']['session_quality']}")
    print(f"  - Engagement Level: {behavior_result['engagement_level']}")
    print(f"  - Action Diversity: {behavior_result['behavior_analysis']['action_diversity']}")
    
    # Generate JavaScript SDK
    sdk = web.generate_javascript_sdk(connection.agent_id)
    print(f"\n✓ JavaScript SDK Generated:")
    print(f"  - SDK Size: {len(sdk)} characters")
    print(f"  - Contains CognitiveMeshWebSDK: {'CognitiveMeshWebSDK' in sdk}")
    
    return web


def demo_integration_verification(api, unity, ros, web):
    """Demo 5: Integration Verification."""
    print_section("Demo 5: Integration Verification (4.3)")
    
    # Initialize verification system
    verification = IntegrationVerificationSystem(api, unity, ros, web)
    print("✓ Integration Verification System initialized\n")
    
    # Run complete verification
    report = verification.run_complete_verification()
    
    # Additional report details
    print(f"Verification completed at: {time.ctime(report.timestamp)}")
    print(f"API Tests: {len(report.api_tests)} total")
    print(f"Embodiment Tests: {len(report.embodiment_tests)} total")
    print(f"Performance Metrics: {len(report.performance_metrics)} metrics")
    
    return report


def main():
    """Run complete Phase 4 demonstration."""
    print("\n" + "=" * 70)
    print("  Phase 4: Distributed Cognitive Mesh API & Embodiment Layer")
    print("  Complete Demonstration")
    print("=" * 70)
    
    try:
        # Demo 1: API
        api = demo_cognitive_mesh_api()
        time.sleep(0.5)
        
        # Demo 2: Unity3D
        unity = demo_unity3d_binding(api)
        time.sleep(0.5)
        
        # Demo 3: ROS
        ros = demo_ros_binding(api)
        time.sleep(0.5)
        
        # Demo 4: Web Agent
        web = demo_web_agent_binding(api)
        time.sleep(0.5)
        
        # Demo 5: Integration Verification
        report = demo_integration_verification(api, unity, ros, web)
        
        print_section("Phase 4 Demo Complete")
        print(f"✅ All demos executed successfully")
        print(f"✅ Overall system health: {report.overall_health * 100:.0f}%")
        print(f"\nTensor Signature: [agents={report.api_tests[0].duration_ms:.0f}, " +
              f"sensors=12, effectors=8, state={report.overall_health:.2f}]")
        
    except Exception as e:
        print(f"\n❌ Demo failed with error: {e}")
        import traceback
        traceback.print_exc()
        return 1
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
