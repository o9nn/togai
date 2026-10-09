"""
Unity3D Binding for Cognitive Mesh

Provides seamless integration with Unity3D game engines:
- GameObject transform synchronization
- Physics event handling
- Animation state management
- Real-time scene recommendations
"""

from dataclasses import asdict, dataclass
from enum import Enum
from typing import Any, Dict, List, Optional


# ============================================================================
# Data Classes
# ============================================================================


@dataclass
class Vector3:
    """3D vector for Unity transforms."""
    
    x: float = 0.0
    y: float = 0.0
    z: float = 0.0
    
    @staticmethod
    def zero():
        """Create a zero vector."""
        return Vector3(0, 0, 0)
    
    @staticmethod
    def one():
        """Create a unit vector."""
        return Vector3(1, 1, 1)


@dataclass
class UnityTransform:
    """Unity GameObject transform data."""
    
    position: Vector3
    rotation: Vector3
    scale: Vector3
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary."""
        return {
            "position": asdict(self.position),
            "rotation": asdict(self.rotation),
            "scale": asdict(self.scale),
        }


class UnityEventType(Enum):
    """Unity event types."""
    
    COLLISION = "COLLISION"
    TRIGGER = "TRIGGER"
    INPUT = "INPUT"
    ANIMATION_COMPLETE = "ANIMATION_COMPLETE"
    AUDIO = "AUDIO"


@dataclass
class UnityConnection:
    """Unity client connection information."""
    
    agent_id: str
    success: bool
    message: str
    client_name: str
    capabilities: List[str]
    timestamp: float


# ============================================================================
# Unity3D Binding
# ============================================================================


class Unity3DBinding:
    """
    Unity3D integration binding for cognitive mesh.
    
    Provides GameObject synchronization, event handling,
    and cognitive recommendations for Unity games.
    """
    
    def __init__(self, mesh_api):
        """
        Initialize Unity3D binding.
        
        Args:
            mesh_api: CognitiveMeshAPI instance
        """
        self.mesh_api = mesh_api
        self.transforms: Dict[str, Dict[str, UnityTransform]] = {}
        self.animations: Dict[str, Dict[str, str]] = {}
        
        print("[Unity3DBinding] Initialized")
    
    def connect_unity_client(
        self,
        client_name: str,
        game_object_capabilities: List[str],
        scene_id: str = "MainScene",
    ) -> UnityConnection:
        """
        Connect a Unity3D client to the mesh.
        
        Args:
            client_name: Name of the Unity client
            game_object_capabilities: List of GameObject capabilities
            scene_id: Unity scene identifier
        
        Returns:
            UnityConnection with agent info
        """
        import time
        
        # Register with mesh API
        registration = self.mesh_api.register_agent(
            client_name=client_name,
            capabilities=game_object_capabilities,
            agent_type="unity3d",
        )
        
        if registration.success:
            # Initialize transform and animation tracking
            self.transforms[registration.agent_id] = {}
            self.animations[registration.agent_id] = {}
            
            print(f"[Unity3DBinding] Connected client: {client_name} ({registration.agent_id})")
        
        return UnityConnection(
            agent_id=registration.agent_id,
            success=registration.success,
            message=registration.message,
            client_name=client_name,
            capabilities=game_object_capabilities,
            timestamp=time.time(),
        )
    
    def update_game_object_transform(
        self,
        agent_id: str,
        game_object_id: str,
        transform: UnityTransform,
    ) -> Dict[str, Any]:
        """
        Update GameObject transform data.
        
        Args:
            agent_id: Agent identifier
            game_object_id: GameObject identifier
            transform: Transform data
        
        Returns:
            Response with update status
        """
        if agent_id not in self.transforms:
            return {
                "success": False,
                "error": "Agent not registered",
            }
        
        # Store transform
        self.transforms[agent_id][game_object_id] = transform
        
        # Submit as sensor data
        sensor_data = {
            "gameObjectId": game_object_id,
            "transform": transform.to_dict(),
        }
        
        result = self.mesh_api.submit_sensor_data(
            agent_id=agent_id,
            sensor_type="unity_transform",
            sensor_data=sensor_data,
        )
        
        return {
            "success": result.get("success", False),
            "agent_id": agent_id,
            "game_object_id": game_object_id,
            "transform_updated": True,
        }
    
    def handle_unity_event(
        self,
        agent_id: str,
        event_type: UnityEventType,
        event_data: Dict[str, Any],
    ) -> Dict[str, Any]:
        """
        Handle Unity event and generate cognitive response.
        
        Args:
            agent_id: Agent identifier
            event_type: Type of Unity event
            event_data: Event data dictionary
        
        Returns:
            Response with recommendations
        """
        if agent_id not in self.transforms:
            return {
                "success": False,
                "error": "Agent not registered",
            }
        
        # Submit event as sensor data
        sensor_data = {
            "eventType": event_type.value,
            "eventData": event_data,
        }
        
        self.mesh_api.submit_sensor_data(
            agent_id=agent_id,
            sensor_type="unity_event",
            sensor_data=sensor_data,
        )
        
        # Generate recommendations based on event type
        recommendations = self._generate_event_recommendations(
            event_type, event_data
        )
        
        return {
            "success": True,
            "agent_id": agent_id,
            "event_type": event_type.value,
            "recommendations": recommendations,
            "cognitive_response": self._get_cognitive_response(event_type),
        }
    
    def get_scene_recommendations(
        self,
        agent_id: str,
        scene_context: Dict[str, Any],
    ) -> Dict[str, Any]:
        """
        Get cognitive recommendations for Unity scene.
        
        Args:
            agent_id: Agent identifier
            scene_context: Current scene context
        
        Returns:
            Recommendations for scene optimization
        """
        if agent_id not in self.transforms:
            return {
                "success": False,
                "error": "Agent not registered",
            }
        
        # Analyze scene context
        object_count = scene_context.get("objectCount", 0)
        active_physics = scene_context.get("activePhysics", 0)
        
        recommendations = []
        
        if object_count > 100:
            recommendations.append({
                "type": "optimization",
                "priority": "high",
                "suggestion": "Consider object pooling for better performance",
            })
        
        if active_physics > 50:
            recommendations.append({
                "type": "physics",
                "priority": "medium",
                "suggestion": "Enable physics LOD for distant objects",
            })
        
        return {
            "success": True,
            "agent_id": agent_id,
            "scene_recommendations": recommendations,
            "performance_score": self._calculate_performance_score(scene_context),
        }
    
    def _generate_event_recommendations(
        self,
        event_type: UnityEventType,
        event_data: Dict[str, Any],
    ) -> List[Dict[str, Any]]:
        """Generate recommendations based on event type."""
        recommendations = []
        
        if event_type == UnityEventType.COLLISION:
            force = event_data.get("force", 0.0)
            if force > 10.0:
                recommendations.append({
                    "action": "trigger_particle_effect",
                    "priority": "high",
                    "params": {"intensity": min(force / 20.0, 1.0)},
                })
        
        elif event_type == UnityEventType.INPUT:
            input_type = event_data.get("inputType", "")
            if input_type == "click":
                recommendations.append({
                    "action": "highlight_object",
                    "priority": "medium",
                    "params": {"duration": 0.5},
                })
        
        elif event_type == UnityEventType.ANIMATION_COMPLETE:
            recommendations.append({
                "action": "transition_to_idle",
                "priority": "low",
                "params": {"blend_time": 0.2},
            })
        
        return recommendations
    
    def _get_cognitive_response(self, event_type: UnityEventType) -> str:
        """Get cognitive response for event type."""
        responses = {
            UnityEventType.COLLISION: "Collision detected - analyzing impact",
            UnityEventType.TRIGGER: "Trigger activated - processing context",
            UnityEventType.INPUT: "Input received - generating response",
            UnityEventType.ANIMATION_COMPLETE: "Animation complete - ready for next state",
            UnityEventType.AUDIO: "Audio event processed - updating scene state",
        }
        return responses.get(event_type, "Event processed")
    
    def _calculate_performance_score(self, scene_context: Dict[str, Any]) -> float:
        """Calculate scene performance score (0-1)."""
        object_count = scene_context.get("objectCount", 0)
        active_physics = scene_context.get("activePhysics", 0)
        draw_calls = scene_context.get("drawCalls", 0)
        
        # Simple scoring based on typical Unity performance metrics
        object_score = max(0.0, 1.0 - (object_count / 500.0))
        physics_score = max(0.0, 1.0 - (active_physics / 200.0))
        draw_call_score = max(0.0, 1.0 - (draw_calls / 1000.0))
        
        return (object_score + physics_score + draw_call_score) / 3.0
