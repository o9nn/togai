package org.ninelym.layla.calendar

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.askJson
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Calendar Integration Service
 * Phase 2: Enhancement - Calendar management with AI
 * 
 * Provides calendar integration with AI-powered event creation and scheduling.
 */
class CalendarService(
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() },
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    
    private val events = ConcurrentHashMap<String, CalendarEvent>()
    private val calendars = mutableListOf<CalendarInfo>()
    
    private var isInitialized = false
    private var defaultCalendarId: String? = null
    
    /**
     * Initialize calendar service
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            loadCalendars()
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all calendars
     */
    fun getCalendars(): List<CalendarInfo> {
        return calendars.toList()
    }
    
    /**
     * Set default calendar
     */
    fun setDefaultCalendar(calendarId: String): Result<Unit> {
        return try {
            if (calendars.any { it.id == calendarId }) {
                defaultCalendarId = calendarId
                Result.success(Unit)
            } else {
                Result.failure(Exception("Calendar not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create event from natural language
     */
    suspend fun createEventFromNaturalLanguage(
        description: String
    ): Result<CalendarEvent> {
        if (!isInitialized) {
            return Result.failure(Exception("Service not initialized"))
        }
        
        return try {
            // Use AI to parse natural language into event details
            val eventDetails = parseEventDetails(description)
            
            // Create calendar event
            val event = CalendarEvent(
                id = generateEventId(),
                calendarId = defaultCalendarId ?: calendars.firstOrNull()?.id ?: "",
                title = eventDetails.title,
                description = eventDetails.description,
                startTime = eventDetails.startTime,
                endTime = eventDetails.endTime,
                location = eventDetails.location,
                reminder = eventDetails.reminder
            )
            
            events[event.id] = event
            
            Result.success(event)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get events for a date range
     */
    fun getEvents(
        startTime: Long,
        endTime: Long,
        calendarId: String? = null
    ): List<CalendarEvent> {
        return events.values.filter { event ->
            val inRange = event.startTime >= startTime && event.startTime <= endTime
            val inCalendar = calendarId == null || event.calendarId == calendarId
            inRange && inCalendar
        }.sortedBy { it.startTime }
    }
    
    /**
     * Get upcoming events
     */
    fun getUpcomingEvents(count: Int = 10): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        return events.values
            .filter { it.startTime > now }
            .sortedBy { it.startTime }
            .take(count)
    }
    
    /**
     * Create event
     */
    fun createEvent(event: CalendarEvent): Result<String> {
        return try {
            val eventId = event.id.ifEmpty { generateEventId() }
            val eventWithId = event.copy(id = eventId)
            events[eventId] = eventWithId
            Result.success(eventId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update event
     */
    fun updateEvent(eventId: String, event: CalendarEvent): Result<Unit> {
        return try {
            if (!events.containsKey(eventId)) {
                return Result.failure(Exception("Event not found"))
            }
            events[eventId] = event.copy(id = eventId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete event
     */
    fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            events.remove(eventId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Suggest meeting times using AI
     */
    suspend fun suggestMeetingTimes(
        duration: Long,
        participants: List<String>,
        preferences: MeetingPreferences
    ): List<TimeSlot> {
        // Use AI to analyze calendar and suggest optimal meeting times
        val now = System.currentTimeMillis()
        val endOfWeek = now + (7 * 24 * 60 * 60 * 1000L)
        
        val busySlots = getEvents(now, endOfWeek)
        
        // Free slots, honouring time-of-day, avoided days and breaks between meetings
        val breakMs = preferences.minBreakBetweenMeetings * 60_000L
        val padded = busySlots.map { it.copy(startTime = it.startTime - breakMs, endTime = it.endTime + breakMs) }
        val freeSlots = findFreeSlots(padded, now, endOfWeek, duration).filter { slot ->
            val start = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(slot.startTime), zone)
            val hourOk = when (preferences.preferredTimeOfDay.lowercase()) {
                "morning" -> start.hour in 8..11
                "afternoon" -> start.hour in 12..16
                "evening" -> start.hour in 17..20
                else -> start.hour in 8..18
            }
            val day = start.dayOfWeek
            val weekend = day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY
            val dayOk = preferences.avoidDays.none { avoid ->
                avoid.equals(day.name, ignoreCase = true) || (avoid.equals("weekends", ignoreCase = true) && weekend)
            }
            hourOk && dayOk
        }
        
        return freeSlots.take(5) // Return top 5 suggestions
    }
    
    /**
     * Prepare for meeting using AI
     */
    suspend fun prepareMeeting(eventId: String): Result<MeetingPreparation> {
        val event = events[eventId]
            ?: return Result.failure(Exception("Event not found"))
        
        return try {
            // The model drafts the preparation from the event details
            val json = modelProvider().getOrThrow().askJson(
                "Help the user prepare for a meeting. Respond as JSON: {\"agenda\": string, " +
                    "\"suggestedTopics\": [string], \"preparationMinutes\": integer}.",
                buildString {
                    appendLine("Title: ${event.title}")
                    event.description?.let { appendLine("Description: $it") }
                    event.location?.let { appendLine("Location: $it") }
                    appendLine("Starts: ${format(event.startTime)}, ends: ${format(event.endTime)}")
                }
            ).getOrThrow().jsonObject
            val preparation = MeetingPreparation(
                eventId = eventId,
                agenda = json["agenda"]?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalStateException("Model did not return an agenda"),
                suggestedTopics = (json["suggestedTopics"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                relevantDocuments = emptyList(),
                preparationTime = json["preparationMinutes"]?.jsonPrimitive?.intOrNull ?: 15
            )
            
            Result.success(preparation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun loadCalendars() {
        // Events created here live in Layla's own on-device calendar
        calendars.add(CalendarInfo(
            id = LOCAL_CALENDAR_ID,
            name = "Layla",
            accountName = "On this device",
            color = 0xFF1976D2.toInt()
        ))
        
        defaultCalendarId = LOCAL_CALENDAR_ID
    }
    
    private fun format(epochMillis: Long): String =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    
    /**
     * The model parses the natural-language description relative to the current local time.
     */
    private suspend fun parseEventDetails(description: String): EventDetails {
        val now = ZonedDateTime.now(zone)
        val json = modelProvider().getOrThrow().askJson(
            "Extract a calendar event from the user's text. The current local date-time is " +
                "${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} (${now.dayOfWeek}, zone $zone). " +
                "Respond as JSON: {\"title\": string, \"start\": \"YYYY-MM-DDTHH:MM\" (local time), " +
                "\"durationMinutes\": integer, \"location\": string or null, \"reminderMinutes\": integer or null}. " +
                "If no duration is given use 60.",
            description
        ).getOrThrow().jsonObject
        val title = json["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Model did not extract a title")
        val startText = json["start"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Model did not extract a start time")
        val start = LocalDateTime.parse(startText.take(16)).atZone(zone).toInstant().toEpochMilli()
        val duration = json["durationMinutes"]?.jsonPrimitive?.intOrNull ?: 60
        
        return EventDetails(
            title = title,
            description = description,
            startTime = start,
            endTime = start + duration * 60_000L,
            location = json["location"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" },
            reminder = json["reminderMinutes"]?.jsonPrimitive?.intOrNull
        )
    }
    
    private fun findFreeSlots(
        busySlots: List<CalendarEvent>,
        startTime: Long,
        endTime: Long,
        duration: Long
    ): List<TimeSlot> {
        val freeSlots = mutableListOf<TimeSlot>()
        
        // Simple algorithm to find free slots
        var currentTime = startTime
        
        while (currentTime + duration <= endTime) {
            val slotEnd = currentTime + duration
            val isOccupied = busySlots.any { event ->
                (currentTime >= event.startTime && currentTime < event.endTime) ||
                (slotEnd > event.startTime && slotEnd <= event.endTime)
            }
            
            if (!isOccupied) {
                freeSlots.add(TimeSlot(currentTime, slotEnd))
            }
            
            currentTime += (30 * 60 * 1000L) // Check every 30 minutes
        }
        
        return freeSlots
    }
    
    private fun generateEventId(): String {
        return "evt_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    }
    
    companion object {
        const val LOCAL_CALENDAR_ID = "layla-local"
    }
}

/**
 * Calendar information
 */
data class CalendarInfo(
    val id: String,
    val name: String,
    val accountName: String,
    val color: Int
)

/**
 * Calendar event
 */
data class CalendarEvent(
    val id: String,
    val calendarId: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val location: String?,
    val reminder: Int? // minutes before event
)

/**
 * Event details parsed from natural language
 */
data class EventDetails(
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val location: String?,
    val reminder: Int?
)

/**
 * Time slot for meetings
 */
data class TimeSlot(
    val startTime: Long,
    val endTime: Long
)

/**
 * Meeting preferences
 */
data class MeetingPreferences(
    val preferredTimeOfDay: String, // morning, afternoon, evening
    val avoidDays: List<String>, // weekends, etc.
    val minBreakBetweenMeetings: Int // minutes
)

/**
 * Meeting preparation
 */
data class MeetingPreparation(
    val eventId: String,
    val agenda: String,
    val suggestedTopics: List<String>,
    val relevantDocuments: List<String>,
    val preparationTime: Int // minutes
)
