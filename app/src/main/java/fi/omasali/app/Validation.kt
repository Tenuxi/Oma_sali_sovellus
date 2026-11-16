package fi.omasali.app

import java.util.Base64

const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
private fun validId(s: String) = s.length in 1..100 && s.all { it.isLetterOrDigit() || it in "-_" }
private fun name(s: String) { require(s.isNotBlank() && s.length <= 200) { "Nimi puuttuu tai on liian pitkä" } }
private fun note(s: String) { require(s.length <= 10000) { "Muistiinpano on liian pitkä" } }
private fun weight(g: Long) { require(g in 0..2_000_000) { "Virheellinen paino" } }
private fun unique(ids: List<String>) { require(ids.all(::validId) && ids.distinct().size == ids.size) { "Virheellinen tai toistuva tunniste" } }
fun validateExercise(e: Exercise) {
    require(validId(e.id)); name(e.name); name(e.muscle); require(e.equipment.length <= 200); note(e.notes); weight(e.increment)
    e.photo?.let {
        require(it.length <= 400_000) { "Kuva on liian suuri" }
        val bytes = Base64.getDecoder().decode(it)
        require(bytes.size > 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) { "Kuvan pitää olla JPEG" }
    }
}
private fun target(t: ProgramExercise) { require(validId(t.id) && validId(t.exerciseId)); require(t.sets in 1..30 && t.low in 1..1000 && t.high in t.low..1000); note(t.notes) }
fun validateSession(s: WorkoutSession) {
    require(validId(s.id) && validId(s.programId) && validId(s.dayId)); name(s.programName); name(s.dayName); note(s.notes)
    require(s.started > 0 && s.cycleOrder > 0 && (s.ended == null || s.ended >= s.started))
    require((s.status == Status.FINISHED) == (s.ended != null))
    require(s.exercises.size <= 200); unique(s.exercises.map { it.id }); unique(s.cycleDays)
    s.exercises.forEach { e ->
        validateExercise(e.exercise); target(e.target); require(e.target.exerciseId == e.exercise.id); note(e.notes)
        require(e.sets.size <= 100); unique(e.sets.map { it.id })
        e.sets.forEach { v ->
            weight(v.grams); weight(v.added); weight(v.assistance); v.body?.let { weight(it); require(it > 0) }
            require(v.reps in 0..1000 && (!v.done || v.reps > 0))
            require(v.added == 0L || v.assistance == 0L) { "Lisäpaino ja avustus eivät voi olla samanaikaisia" }
            when (e.exercise.load) {
                Load.EXTERNAL -> require(v.body == null && v.added == 0L && v.assistance == 0L)
                Load.BODY -> require(v.grams == 0L && v.added == 0L && v.assistance == 0L)
                Load.ASSISTED -> require(v.grams == 0L)
            }
        }
    }
}
fun validateRecords(records: List<Record>) {
    require(records.size <= 50000); unique(records.map { it.id })
    records.forEach { r ->
        require(validId(r.revision) && r.ancestors.all(::validId) && r.revision !in r.ancestors)
        when (r.kind) {
            "exercise" -> r.value<Exercise>().also { require(it.id == r.id); validateExercise(it) }
            "program" -> r.value<TrainingProgram>().also { p ->
                require(p.id == r.id); name(p.name); require(p.days.size <= 100); unique(p.days.map { it.id })
                p.days.forEach { d -> name(d.name); require(d.exercises.size <= 200); unique(d.exercises.map { it.id }); d.exercises.forEach(::target) }
            }
            "session" -> r.value<WorkoutSession>().also { require(it.id == r.id); validateSession(it) }
            "weight" -> r.value<BodyWeightEntry>().also { require(it.id == r.id && it.time > 0 && it.grams > 0); weight(it.grams) }
            "settings" -> { require(r.id == "settings" && !r.deleted); r.value<Settings>() }
            else -> error("Tuntematon tietuetyyppi: ${r.kind}")
        }
    }
    val exerciseIds = records.filter { it.kind == "exercise" && !it.deleted }.map { it.id }.toSet()
    records.filter { it.kind == "program" && !it.deleted }.forEach { r ->
        require(r.value<TrainingProgram>().days.flatMap { it.exercises }.all { it.exerciseId in exerciseIds }) { "Ohjelman liike puuttuu. Ratkaise ristiriidat yhdessä." }
    }
}
fun parseBackup(text: String): Backup {
    require(text.toByteArray().size <= MAX_BACKUP_BYTES) { "Varmuuskopio ylittää 32 Mt rajan" }
    return codec.decodeFromString<Backup>(text).also {
        require(it.metadata.format == 1 && it.metadata.applicationId == "fi.omasali.app") { "Varmuuskopion versio tai sovellus ei täsmää" }
        validateRecords(it.records)
    }
}
