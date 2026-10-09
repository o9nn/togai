"""
Web Agent Binding for Cognitive Mesh

Enables cognitive integration with web applications:
- DOM interaction tracking
- User behavior analysis
- Media capture processing
- Real-time UI recommendations
- Automatic JavaScript SDK generation
"""

from dataclasses import asdict, dataclass
from enum import Enum
from typing import Any, Dict, List, Optional
import time


# ============================================================================
# Enums and Data Classes
# ============================================================================


class DOMInteractionType(Enum):
    """DOM interaction types."""
    
    CLICK = "CLICK"
    SCROLL = "SCROLL"
    INPUT = "INPUT"
    HOVER = "HOVER"
    FORM_SUBMIT = "FORM_SUBMIT"
    NAVIGATION = "NAVIGATION"


@dataclass
class DOMInteraction:
    """DOM interaction data."""
    
    type: DOMInteractionType
    element_id: str
    element_data: Dict[str, Any]
    timestamp: float = 0.0
    
    def __post_init__(self):
        if self.timestamp == 0.0:
            self.timestamp = time.time()


@dataclass
class UserBehaviorData:
    """User behavior analysis data."""
    
    actions: List[str]
    session_duration: int  # milliseconds
    page_views: int
    engagement_score: float  # 0-1
    conversion_event: bool = False


@dataclass
class WebConnection:
    """Web client connection information."""
    
    agent_id: str
    success: bool
    message: str
    client_id: str
    capabilities: List[str]
    timestamp: float


# ============================================================================
# Web Agent Binding
# ============================================================================


class WebAgentBinding:
    """
    Web application integration binding for cognitive mesh.
    
    Provides DOM tracking, behavior analysis, and
    cognitive UI recommendations for web applications.
    """
    
    def __init__(self, mesh_api):
        """
        Initialize Web Agent binding.
        
        Args:
            mesh_api: CognitiveMeshAPI instance
        """
        self.mesh_api = mesh_api
        self.interactions: Dict[str, List[DOMInteraction]] = {}
        self.behavior_data: Dict[str, UserBehaviorData] = {}
        
        print("[WebAgentBinding] Initialized")
    
    def connect_web_client(
        self,
        client_id: str,
        client_capabilities: List[str],
        user_agent: str = "Unknown",
    ) -> WebConnection:
        """
        Connect a web client to the mesh.
        
        Args:
            client_id: Client identifier
            client_capabilities: List of client capabilities
            user_agent: Browser user agent string
        
        Returns:
            WebConnection with agent info
        """
        # Register with mesh API
        registration = self.mesh_api.register_agent(
            client_name=client_id,
            capabilities=client_capabilities,
            agent_type="web",
        )
        
        if registration.success:
            # Initialize interaction and behavior tracking
            self.interactions[registration.agent_id] = []
            
            print(f"[WebAgentBinding] Connected client: {client_id} ({registration.agent_id})")
        
        return WebConnection(
            agent_id=registration.agent_id,
            success=registration.success,
            message=registration.message,
            client_id=client_id,
            capabilities=client_capabilities,
            timestamp=time.time(),
        )
    
    def handle_dom_interaction(
        self,
        agent_id: str,
        interaction: DOMInteraction,
    ) -> Dict[str, Any]:
        """
        Handle DOM interaction and generate cognitive insights.
        
        Args:
            agent_id: Agent identifier
            interaction: DOM interaction data
        
        Returns:
            Response with cognitive insights
        """
        if agent_id not in self.interactions:
            return {
                "success": False,
                "error": "Agent not registered",
            }
        
        # Store interaction
        self.interactions[agent_id].append(interaction)
        
        # Keep only last 100 interactions
        if len(self.interactions[agent_id]) > 100:
            self.interactions[agent_id] = self.interactions[agent_id][-100:]
        
        # Submit to mesh API
        sensor_data = {
            "interactionType": interaction.type.value,
            "elementId": interaction.element_id,
            "elementData": interaction.element_data,
        }
        
        self.mesh_api.submit_sensor_data(
            agent_id=agent_id,
            sensor_type="web_dom_interaction",
            sensor_data=sensor_data,
        )
        
        # Generate cognitive insights
        insights = self._analyze_interaction(interaction)
        recommendations = self._get_interaction_recommendations(interaction, insights)
        
        return {
            "success": True,
            "agent_id": agent_id,
            "interaction_type": interaction.type.value,
            "cognitive_insights": insights,
            "recommendations": recommendations,
            "ui_suggestions": self._get_ui_suggestions(interaction.type),
        }
    
    def process_user_behavior(
        self,
        agent_id: str,
        behavior_data: UserBehaviorData,
    ) -> Dict[str, Any]:
        """
        Process user behavior data and generate insights.
        
        Args:
            agent_id: Agent identifier
            behavior_data: User behavior data
        
        Returns:
            Response with behavior analysis
        """
        if agent_id not in self.interactions:
            return {
                "success": False,
                "error": "Agent not registered",
            }
        
        # Store behavior data
        self.behavior_data[agent_id] = behavior_data
        
        # Submit to mesh API
        sensor_data = {
            "actions": behavior_data.actions,
            "sessionDuration": behavior_data.session_duration,
            "pageViews": behavior_data.page_views,
            "engagementScore": behavior_data.engagement_score,
            "conversionEvent": behavior_data.conversion_event,
        }
        
        self.mesh_api.submit_sensor_data(
            agent_id=agent_id,
            sensor_type="web_user_behavior",
            sensor_data=sensor_data,
        )
        
        # Analyze behavior
        analysis = self._analyze_user_behavior(behavior_data)
        
        return {
            "success": True,
            "agent_id": agent_id,
            "behavior_analysis": analysis,
            "engagement_level": self._get_engagement_level(behavior_data),
            "personalization_recommendations": self._get_personalization_recommendations(behavior_data),
        }
    
    def get_ui_recommendations(
        self,
        agent_id: str,
        current_page: Dict[str, Any],
    ) -> Dict[str, Any]:
        """
        Get cognitive UI recommendations.
        
        Args:
            agent_id: Agent identifier
            current_page: Current page context
        
        Returns:
            UI recommendations
        """
        if agent_id not in self.interactions:
            return {
                "success": False,
                "error": "Agent not registered",
            }
        
        # Analyze page context
        page_complexity = current_page.get("elementCount", 0) / 100.0
        interaction_count = len(self.interactions[agent_id])
        
        recommendations = []
        
        if page_complexity > 1.0:
            recommendations.append({
                "type": "simplification",
                "priority": "high",
                "suggestion": "Consider simplifying page layout",
                "reason": "High element count may overwhelm users",
            })
        
        if interaction_count > 50:
            behavior = self.behavior_data.get(agent_id)
            if behavior and behavior.engagement_score < 0.5:
                recommendations.append({
                    "type": "engagement",
                    "priority": "medium",
                    "suggestion": "Add interactive elements or gamification",
                    "reason": "Low engagement despite high interaction count",
                })
        
        return {
            "success": True,
            "agent_id": agent_id,
            "ui_recommendations": recommendations,
            "accessibility_score": self._calculate_accessibility_score(current_page),
            "cognitive_load": min(1.0, page_complexity),
        }
    
    def generate_javascript_sdk(self, agent_id: str) -> str:
        """
        Generate JavaScript SDK for web integration.
        
        Args:
            agent_id: Agent identifier
        
        Returns:
            JavaScript SDK code
        """
        sdk_code = f"""
/**
 * Cognitive Mesh Web SDK
 * Auto-generated for agent: {agent_id}
 */

class CognitiveMeshWebSDK {{
    constructor(agentId, apiEndpoint = 'ws://localhost:8080') {{
        this.agentId = agentId || '{agent_id}';
        this.apiEndpoint = apiEndpoint;
        this.ws = null;
        this.connected = false;
        
        this.init();
    }}
    
    init() {{
        // Connect to WebSocket
        try {{
            this.ws = new WebSocket(`${{this.apiEndpoint}}/ws/agents/${{this.agentId}}`);
            this.ws.onopen = () => {{
                this.connected = true;
                console.log('[CognitiveMesh] Connected to cognitive mesh');
            }};
            this.ws.onmessage = (event) => this.handleMessage(event);
            this.ws.onerror = (error) => console.error('[CognitiveMesh] Error:', error);
        }} catch (e) {{
            console.warn('[CognitiveMesh] WebSocket not available, using REST fallback');
        }}
        
        // Track DOM interactions
        this.trackDOMInteractions();
    }}
    
    trackDOMInteractions() {{
        // Track clicks
        document.addEventListener('click', (e) => {{
            this.sendDOMInteraction('CLICK', e.target);
        }});
        
        // Track scrolling
        let scrollTimeout;
        window.addEventListener('scroll', () => {{
            clearTimeout(scrollTimeout);
            scrollTimeout = setTimeout(() => {{
                this.sendDOMInteraction('SCROLL', document.documentElement);
            }}, 250);
        }});
        
        // Track form submissions
        document.addEventListener('submit', (e) => {{
            this.sendDOMInteraction('FORM_SUBMIT', e.target);
        }});
    }}
    
    sendDOMInteraction(type, element) {{
        const interaction = {{
            type: type,
            elementId: element.id || 'unknown',
            elementData: {{
                tagName: element.tagName,
                className: element.className,
                innerText: element.innerText?.substring(0, 100)
            }},
            timestamp: Date.now()
        }};
        
        if (this.connected) {{
            this.ws.send(JSON.stringify({{
                type: 'dom_interaction',
                data: interaction
            }}));
        }} else {{
            this.sendREST('/api/v1/agents/' + this.agentId + '/sensors', {{
                sensorType: 'web_dom_interaction',
                sensorData: interaction
            }});
        }}
    }}
    
    async getRecommendations() {{
        const response = await fetch(`/api/v1/agents/${{this.agentId}}/effectors`);
        return response.json();
    }}
    
    async sendREST(endpoint, data) {{
        try {{
            await fetch(endpoint, {{
                method: 'POST',
                headers: {{'Content-Type': 'application/json'}},
                body: JSON.stringify(data)
            }});
        }} catch (e) {{
            console.error('[CognitiveMesh] REST request failed:', e);
        }}
    }}
    
    handleMessage(event) {{
        const message = JSON.parse(event.data);
        console.log('[CognitiveMesh] Received:', message);
        
        if (message.type === 'recommendation') {{
            this.applyRecommendation(message.data);
        }}
    }}
    
    applyRecommendation(recommendation) {{
        console.log('[CognitiveMesh] Applying recommendation:', recommendation);
        // Application-specific recommendation handling
    }}
}}

// Auto-initialize if agent ID is provided
if (typeof COGNITIVE_MESH_AGENT_ID !== 'undefined') {{
    window.cognitiveMesh = new CognitiveMeshWebSDK(COGNITIVE_MESH_AGENT_ID);
}}
"""
        return sdk_code
    
    def _analyze_interaction(self, interaction: DOMInteraction) -> Dict[str, Any]:
        """Analyze DOM interaction for insights."""
        insights = {
            "interaction_type": interaction.type.value,
            "significance": "medium",
            "user_intent": "unknown",
        }
        
        if interaction.type == DOMInteractionType.CLICK:
            element_data = interaction.element_data
            if "button" in element_data.get("tagName", "").lower():
                insights["significance"] = "high"
                insights["user_intent"] = "action_trigger"
        
        elif interaction.type == DOMInteractionType.FORM_SUBMIT:
            insights["significance"] = "high"
            insights["user_intent"] = "data_submission"
        
        return insights
    
    def _get_interaction_recommendations(
        self,
        interaction: DOMInteraction,
        insights: Dict[str, Any],
    ) -> List[Dict[str, Any]]:
        """Get recommendations based on interaction."""
        recommendations = []
        
        if insights.get("significance") == "high":
            recommendations.append({
                "action": "provide_feedback",
                "priority": "high",
                "description": "Show visual feedback for user action",
            })
        
        return recommendations
    
    def _get_ui_suggestions(self, interaction_type: DOMInteractionType) -> List[str]:
        """Get UI suggestions based on interaction type."""
        suggestions = {
            DOMInteractionType.CLICK: ["Add hover states", "Provide immediate feedback"],
            DOMInteractionType.SCROLL: ["Implement lazy loading", "Add scroll indicators"],
            DOMInteractionType.FORM_SUBMIT: ["Show loading state", "Provide clear success message"],
        }
        return suggestions.get(interaction_type, [])
    
    def _analyze_user_behavior(self, behavior_data: UserBehaviorData) -> Dict[str, Any]:
        """Analyze user behavior patterns."""
        avg_time_per_page = behavior_data.session_duration / max(behavior_data.page_views, 1)
        
        analysis = {
            "session_quality": "good" if behavior_data.engagement_score > 0.6 else "needs_improvement",
            "avg_time_per_page_ms": avg_time_per_page,
            "action_diversity": len(set(behavior_data.actions)),
            "conversion_status": "converted" if behavior_data.conversion_event else "ongoing",
        }
        
        return analysis
    
    def _get_engagement_level(self, behavior_data: UserBehaviorData) -> str:
        """Determine user engagement level."""
        score = behavior_data.engagement_score
        
        if score >= 0.8:
            return "highly_engaged"
        elif score >= 0.5:
            return "engaged"
        elif score >= 0.3:
            return "passive"
        else:
            return "disengaged"
    
    def _get_personalization_recommendations(
        self,
        behavior_data: UserBehaviorData,
    ) -> List[Dict[str, Any]]:
        """Get personalization recommendations."""
        recommendations = []
        
        if behavior_data.engagement_score < 0.5:
            recommendations.append({
                "type": "content",
                "suggestion": "Personalize content based on user interests",
                "priority": "high",
            })
        
        if behavior_data.page_views > 10:
            recommendations.append({
                "type": "navigation",
                "suggestion": "Simplify navigation or add shortcuts",
                "priority": "medium",
            })
        
        return recommendations
    
    def _calculate_accessibility_score(self, page_context: Dict[str, Any]) -> float:
        """Calculate page accessibility score (0-1)."""
        # Simplified accessibility scoring
        has_alt_text = page_context.get("hasAltText", True)
        has_aria_labels = page_context.get("hasAriaLabels", True)
        has_keyboard_nav = page_context.get("hasKeyboardNav", True)
        
        score = sum([has_alt_text, has_aria_labels, has_keyboard_nav]) / 3.0
        return score
