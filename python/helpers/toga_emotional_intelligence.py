"""
Advanced Emotional Intelligence for Toga Personality System

This module provides advanced emotional intelligence capabilities including
sentiment analysis, emotional context understanding, and empathetic response
generation.
"""

import json
from dataclasses import dataclass
from enum import Enum
from typing import Dict, List, Optional, Tuple

from .llm_client import LanguageModel, ModelError, current_model


class EmotionCategory(Enum):
    """Categories of emotions."""

    JOY = "joy"
    SADNESS = "sadness"
    ANGER = "anger"
    FEAR = "fear"
    SURPRISE = "surprise"
    DISGUST = "disgust"
    TRUST = "trust"
    ANTICIPATION = "anticipation"


class SentimentPolarity(Enum):
    """Sentiment polarity classification."""

    VERY_POSITIVE = "very_positive"
    POSITIVE = "positive"
    NEUTRAL = "neutral"
    NEGATIVE = "negative"
    VERY_NEGATIVE = "very_negative"


@dataclass
class EmotionalContext:
    """Emotional context of a message or interaction."""

    primary_emotion: EmotionCategory
    secondary_emotion: Optional[EmotionCategory]
    intensity: float  # 0.0 to 1.0
    sentiment: SentimentPolarity
    confidence: float  # 0.0 to 1.0
    keywords: List[str]


@dataclass
class EmpatheticResponse:
    """Empathetic response configuration."""

    acknowledgment: str
    validation: str
    support: str
    tone_adjustment: float  # -1.0 to 1.0 (negative = softer, positive = more energetic)


class EmotionalIntelligenceEngine:
    """
    Advanced emotional intelligence engine for Toga.

    Provides sentiment analysis, emotion detection, and empathetic
    response generation capabilities.
    """

    def __init__(self, model: Optional[LanguageModel] = None):
        """Initialize emotional intelligence engine; ``model`` writes the empathetic replies."""
        self._model = model
        self.emotion_keywords = self._initialize_emotion_keywords()
        self.sentiment_keywords = self._initialize_sentiment_keywords()

    @property
    def model(self) -> LanguageModel:
        """The model that writes empathetic responses."""
        if self._model is None:
            self._model = current_model()
        return self._model

    def _initialize_emotion_keywords(self) -> Dict[EmotionCategory, List[str]]:
        """Initialize emotion detection keywords."""
        return {
            EmotionCategory.JOY: [
                "happy",
                "excited",
                "wonderful",
                "amazing",
                "great",
                "love",
                "joy",
                "delighted",
                "thrilled",
                "cheerful",
            ],
            EmotionCategory.SADNESS: [
                "sad",
                "unhappy",
                "depressed",
                "down",
                "miserable",
                "upset",
                "disappointed",
                "lonely",
                "hurt",
                "crying",
            ],
            EmotionCategory.ANGER: [
                "angry",
                "mad",
                "furious",
                "annoyed",
                "frustrated",
                "irritated",
                "rage",
                "hate",
                "pissed",
                "outraged",
            ],
            EmotionCategory.FEAR: [
                "scared",
                "afraid",
                "terrified",
                "anxious",
                "worried",
                "nervous",
                "panic",
                "frightened",
                "fearful",
                "concerned",
            ],
            EmotionCategory.SURPRISE: [
                "surprised",
                "shocked",
                "amazed",
                "astonished",
                "stunned",
                "unexpected",
                "wow",
                "incredible",
                "unbelievable",
            ],
            EmotionCategory.TRUST: [
                "trust",
                "believe",
                "confident",
                "reliable",
                "safe",
                "secure",
                "faith",
                "depend",
            ],
            EmotionCategory.ANTICIPATION: [
                "excited",
                "looking forward",
                "anticipate",
                "expect",
                "hope",
                "eager",
                "waiting",
                "ready",
            ],
        }

    def _initialize_sentiment_keywords(self) -> Dict[str, List[str]]:
        """Initialize sentiment analysis keywords."""
        return {
            "positive": [
                "good",
                "great",
                "excellent",
                "wonderful",
                "amazing",
                "love",
                "best",
                "perfect",
                "awesome",
                "fantastic",
            ],
            "negative": [
                "bad",
                "terrible",
                "awful",
                "horrible",
                "worst",
                "hate",
                "poor",
                "disappointing",
                "useless",
                "fail",
            ],
            "intensifiers": ["very", "extremely", "really", "so", "absolutely", "totally"],
        }

    def analyze_emotional_context(self, text: str) -> EmotionalContext:
        """
        Analyze emotional context of text.

        Args:
            text: Input text to analyze

        Returns:
            EmotionalContext with detected emotions and sentiment
        """
        text_lower = text.lower()
        words = text_lower.split()

        # Detect emotions
        emotion_scores: Dict[EmotionCategory, float] = {}

        for emotion, keywords in self.emotion_keywords.items():
            score = sum(1 for keyword in keywords if keyword in text_lower)
            if score > 0:
                emotion_scores[emotion] = score

        # Determine primary and secondary emotions
        if emotion_scores:
            sorted_emotions = sorted(emotion_scores.items(), key=lambda x: x[1], reverse=True)
            primary_emotion = sorted_emotions[0][0]
            secondary_emotion = sorted_emotions[1][0] if len(sorted_emotions) > 1 else None
            intensity = min(1.0, sorted_emotions[0][1] / 3.0)  # Normalize to 0-1
            confidence = min(1.0, sum(emotion_scores.values()) / 5.0)
        else:
            primary_emotion = EmotionCategory.TRUST  # Default neutral emotion
            secondary_emotion = None
            intensity = 0.3
            confidence = 0.5

        # Analyze sentiment
        sentiment = self._analyze_sentiment(text_lower, words)

        # Extract emotional keywords
        keywords = self._extract_emotional_keywords(text_lower)

        return EmotionalContext(
            primary_emotion=primary_emotion,
            secondary_emotion=secondary_emotion,
            intensity=intensity,
            sentiment=sentiment,
            confidence=confidence,
            keywords=keywords,
        )

    def _analyze_sentiment(self, text: str, words: List[str]) -> SentimentPolarity:
        """Analyze sentiment polarity of text."""
        positive_score = sum(
            1 for keyword in self.sentiment_keywords["positive"] if keyword in text
        )
        negative_score = sum(
            1 for keyword in self.sentiment_keywords["negative"] if keyword in text
        )

        # Check for intensifiers
        has_intensifier = any(
            intensifier in text for intensifier in self.sentiment_keywords["intensifiers"]
        )

        if has_intensifier:
            positive_score *= 1.5
            negative_score *= 1.5

        net_score = positive_score - negative_score

        if net_score >= 3:
            return SentimentPolarity.VERY_POSITIVE
        elif net_score >= 1:
            return SentimentPolarity.POSITIVE
        elif net_score <= -3:
            return SentimentPolarity.VERY_NEGATIVE
        elif net_score <= -1:
            return SentimentPolarity.NEGATIVE
        else:
            return SentimentPolarity.NEUTRAL

    def _extract_emotional_keywords(self, text: str) -> List[str]:
        """Extract emotional keywords from text."""
        keywords = []

        for emotion_keywords in self.emotion_keywords.values():
            for keyword in emotion_keywords:
                if keyword in text:
                    keywords.append(keyword)

        for sentiment_keywords in self.sentiment_keywords.values():
            for keyword in sentiment_keywords:
                if keyword in text:
                    keywords.append(keyword)

        return list(set(keywords))[:10]  # Return up to 10 unique keywords

    def generate_empathetic_response(
        self, context: EmotionalContext, message: Optional[str] = None
    ) -> EmpatheticResponse:
        """
        Generate an empathetic response with the language model.

        Args:
            context: Emotional context to respond to
            message: The user's message, if available

        Returns:
            EmpatheticResponse whose acknowledgment, validation and support were
            written by the model; tone_adjustment is computed from the context
        """
        emotion = context.primary_emotion

        # Adjust tone based on emotion and intensity
        tone_adjustments = {
            EmotionCategory.JOY: 0.8,
            EmotionCategory.SADNESS: -0.6,
            EmotionCategory.ANGER: -0.3,
            EmotionCategory.FEAR: -0.5,
            EmotionCategory.SURPRISE: 0.4,
            EmotionCategory.TRUST: 0.3,
            EmotionCategory.ANTICIPATION: 0.6,
        }
        tone_adjustment = tone_adjustments.get(emotion, 0.0) * context.intensity

        system = (
            "You are Toga, a warm, playful and caring AI companion. The user feels "
            f"{emotion.name.lower()} (intensity {context.intensity:.1f}, sentiment "
            f"{context.sentiment.name.lower().replace('_', ' ')}"
            + (f", emotional words: {', '.join(context.keywords)}" if context.keywords else "")
            + "). "
            + ("Be gentle and soft. " if tone_adjustment < 0 else "Match their energy. ")
            + "Respond with a JSON object with three string fields: "
            '"acknowledgment" (one sentence acknowledging the feeling), '
            '"validation" (one sentence validating it) and "support" (one sentence offering support). '
            "Write the sentences yourself."
        )
        user = message or f"(The user is feeling {emotion.name.lower()}.)"
        fields = ("acknowledgment", "validation", "support")
        last_error: Optional[Exception] = None
        # Small local models sometimes return malformed JSON; ask the model again rather than fill gaps
        for _ in range(3):
            raw = self.model.ask(system, user, temperature=0.7, json_mode=True)
            try:
                parsed = self._parse_json(raw)
                parts = [str(parsed[f]).strip() for f in fields]
            except (ModelError, KeyError, TypeError) as e:
                last_error = e
                continue
            if all(parts):
                return EmpatheticResponse(
                    acknowledgment=parts[0],
                    validation=parts[1],
                    support=parts[2],
                    tone_adjustment=tone_adjustment,
                )
            last_error = ModelError(f"Model left an empathetic field empty: {raw[:200]}")
        raise ModelError(f"Model did not produce a complete empathetic response: {last_error}")

    @staticmethod
    def _parse_json(text: str) -> dict:
        start, end = text.find("{"), text.rfind("}")
        if start < 0 or end < start:
            raise ModelError(f"Model did not return JSON: {text[:200]}")
        try:
            return json.loads(text[start : end + 1])
        except json.JSONDecodeError as e:
            raise ModelError(f"Model returned invalid JSON: {text[:200]}") from e

    def detect_emotional_triggers(self, text: str) -> List[Tuple[str, EmotionCategory]]:
        """
        Detect specific emotional triggers in text.

        Args:
            text: Input text

        Returns:
            List of (trigger_phrase, emotion) tuples
        """
        triggers = []
        text_lower = text.lower()

        for emotion, keywords in self.emotion_keywords.items():
            for keyword in keywords:
                if keyword in text_lower:
                    triggers.append((keyword, emotion))

        return triggers

    def calculate_emotional_compatibility(
        self, user_emotion: EmotionCategory, toga_emotion: EmotionCategory
    ) -> float:
        """
        Calculate emotional compatibility between user and Toga.

        Args:
            user_emotion: User's current emotion
            toga_emotion: Toga's current emotion

        Returns:
            Compatibility score (0.0 to 1.0)
        """
        # Emotion compatibility matrix
        compatibility = {
            (EmotionCategory.JOY, EmotionCategory.JOY): 1.0,
            (EmotionCategory.SADNESS, EmotionCategory.TRUST): 0.9,
            (EmotionCategory.ANGER, EmotionCategory.TRUST): 0.7,
            (EmotionCategory.FEAR, EmotionCategory.TRUST): 0.9,
            (EmotionCategory.SURPRISE, EmotionCategory.JOY): 0.8,
            (EmotionCategory.TRUST, EmotionCategory.TRUST): 1.0,
            (EmotionCategory.ANTICIPATION, EmotionCategory.JOY): 0.9,
        }

        return compatibility.get((user_emotion, toga_emotion), 0.5)


def create_emotional_intelligence_engine(
    model: Optional[LanguageModel] = None,
) -> EmotionalIntelligenceEngine:
    """
    Factory function to create emotional intelligence engine.

    Returns:
        Initialized EmotionalIntelligenceEngine
    """
    return EmotionalIntelligenceEngine(model=model)
