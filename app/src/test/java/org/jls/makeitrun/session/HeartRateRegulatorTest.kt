package org.jls.makeitrun.session

import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.heartrate.HeartRateMeasurement
import org.jls.makeitrun.heartrate.HeartRateSample
import org.jls.makeitrun.workout.model.HeartRateTarget
import org.jls.makeitrun.workout.model.RegulationResponsiveness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateRegulatorTest {

    @Test
    fun `without any measurement the speed stays put and the regulator says it is waiting`() {
        val regulator = regulator(initialSpeedKmh = 9.0)

        val outcome = regulator.update(nowElapsedMillis = 0L, sample = null)

        assertEquals(9.0, outcome.targetSpeedKmh, DELTA)
        assertFalse(outcome.speedChanged)
        assertNull(outcome.smoothedBpm)
        assertEquals(ZoneStatus.UNKNOWN, outcome.zone)
        assertEquals(RegulationAlert.WAITING_FOR_SIGNAL, outcome.alert)
    }

    @Test
    fun `the initial speed is brought inside the range the treadmill publishes`() {
        val regulator = regulator(initialSpeedKmh = 99.0)

        val outcome = regulator.update(nowElapsedMillis = 0L, sample = null)

        assertEquals(16.0, outcome.targetSpeedKmh, DELTA)
    }

    @Test
    fun `a heart rate below the zone speeds the treadmill up by one step`() {
        val regulator = regulator(initialSpeedKmh = 9.0)

        val run = regulator.feed(bpm = 120, untilMillis = WARM_UP_OVER)

        assertEquals(1, run.corrections)
        assertEquals(9.5, run.last.targetSpeedKmh, DELTA)
        assertEquals(ZoneStatus.BELOW, run.last.zone)
        assertNull(run.last.alert)
    }

    @Test
    fun `a heart rate above the zone slows the treadmill down by one step`() {
        val regulator = regulator(initialSpeedKmh = 9.0)

        val run = regulator.feed(bpm = 175, untilMillis = WARM_UP_OVER)

        assertEquals(1, run.corrections)
        assertEquals(8.5, run.last.targetSpeedKmh, DELTA)
        assertEquals(ZoneStatus.ABOVE, run.last.zone)
    }

    @Test
    fun `a heart rate inside the zone leaves the speed alone`() {
        val regulator = regulator(initialSpeedKmh = 9.0)

        val run = regulator.feed(bpm = 150, untilMillis = WARM_UP_OVER)

        assertEquals(0, run.corrections)
        assertEquals(9.0, run.last.targetSpeedKmh, DELTA)
        assertEquals(ZoneStatus.IN_ZONE, run.last.zone)
        assertNull(run.last.alert)
    }

    @Test
    fun `no correction is made before the smoothing has had time to settle`() {
        val regulator = regulator(initialSpeedKmh = 9.0)

        val run = regulator.feed(bpm = 120, untilMillis = HeartRateRegulator.WARM_UP_MILLIS - 1)

        assertEquals(0, run.corrections)
        assertEquals(9.0, run.last.targetSpeedKmh, DELTA)
        assertEquals(ZoneStatus.BELOW, run.last.zone)
    }

    @Test
    fun `a heart rate that stays low only triggers one correction per settle window`() {
        val regulator = regulator(initialSpeedKmh = 9.0)

        val run = regulator.feed(bpm = 120, untilMillis = FIRST_CORRECTION_AT + SETTLE - 1)

        assertEquals(1, run.corrections)
        assertEquals(9.5, run.last.targetSpeedKmh, DELTA)
    }

    @Test
    fun `once the settle window has elapsed the correction resumes`() {
        val regulator = regulator(initialSpeedKmh = 9.0)

        val run = regulator.feed(bpm = 120, untilMillis = FIRST_CORRECTION_AT + SETTLE)

        assertEquals(2, run.corrections)
        assertEquals(10.0, run.last.targetSpeedKmh, DELTA)
    }

    @Test
    fun `a silent sensor freezes the speed instead of reading it as a drop in effort`() {
        val regulator = regulator(initialSpeedKmh = 9.0)
        val run = regulator.feed(bpm = 120, untilMillis = WARM_UP_OVER)
        assertEquals(9.5, run.last.targetSpeedKmh, DELTA)

        val outcome = regulator.update(
            nowElapsedMillis = WARM_UP_OVER + HeartRateRegulator.SIGNAL_TIMEOUT_MILLIS + 1,
            sample = sample(bpm = 120, atMillis = WARM_UP_OVER),
        )

        assertEquals(9.5, outcome.targetSpeedKmh, DELTA)
        assertFalse(outcome.speedChanged)
        assertEquals(ZoneStatus.UNKNOWN, outcome.zone)
        assertEquals(RegulationAlert.SIGNAL_LOST, outcome.alert)
    }

    @Test
    fun `the speed stays frozen for as long as the sensor keeps quiet`() {
        val regulator = regulator(initialSpeedKmh = 9.0)
        regulator.feed(bpm = 120, untilMillis = WARM_UP_OVER)
        val stale = sample(bpm = 120, atMillis = WARM_UP_OVER)

        regulator.update(WARM_UP_OVER + 60_000L, stale)
        val outcome = regulator.update(WARM_UP_OVER + 120_000L, stale)

        assertEquals(9.5, outcome.targetSpeedKmh, DELTA)
        assertEquals(RegulationAlert.SIGNAL_LOST, outcome.alert)
    }

    @Test
    fun `a watch taken off the wrist is reported as such rather than as a lost signal`() {
        val regulator = regulator(initialSpeedKmh = 9.0)
        regulator.feed(bpm = 120, untilMillis = WARM_UP_OVER)

        val offWrist = HeartRateSample(
            measurement = HeartRateMeasurement(
                beatsPerMinute = 42,
                sensorContact = HeartRateMeasurement.SensorContact.NOT_DETECTED,
                energyExpendedKiloJoules = null,
                rrIntervalsMillis = emptyList(),
            ),
            receivedAtElapsedMillis = WARM_UP_OVER + SAMPLE_PERIOD,
        )
        val outcome = regulator.update(
            nowElapsedMillis = WARM_UP_OVER + HeartRateRegulator.SIGNAL_TIMEOUT_MILLIS + 1,
            sample = offWrist,
        )

        assertEquals(9.5, outcome.targetSpeedKmh, DELTA)
        assertEquals(RegulationAlert.SENSOR_NOT_WORN, outcome.alert)
    }

    @Test
    fun `a heart rate that stays low at the top speed is reported instead of being chased`() {
        val regulator = regulator(initialSpeedKmh = 16.0)

        val run = regulator.feed(bpm = 120, untilMillis = WARM_UP_OVER)

        assertEquals(0, run.corrections)
        assertEquals(16.0, run.last.targetSpeedKmh, DELTA)
        assertEquals(ZoneStatus.BELOW, run.last.zone)
        assertEquals(RegulationAlert.SPEED_AT_MAXIMUM, run.last.alert)
    }

    @Test
    fun `a heart rate that stays high at the lowest speed is reported`() {
        val regulator = regulator(initialSpeedKmh = 1.0)

        val run = regulator.feed(bpm = 180, untilMillis = WARM_UP_OVER)

        assertEquals(0, run.corrections)
        assertEquals(1.0, run.last.targetSpeedKmh, DELTA)
        assertEquals(RegulationAlert.SPEED_AT_MINIMUM, run.last.alert)
    }

    @Test
    fun `corrections land on the increment the treadmill actually accepts`() {
        val regulator = HeartRateRegulator(
            target = TARGET,
            speedRange = TreadmillCapabilities.ValueRange(
                minimum = 1.0,
                maximum = 16.0,
                increment = 0.5,
            ),
            responsiveness = RegulationResponsiveness.NORMAL,
            initialSpeedKmh = 9.2,
        )

        val run = regulator.feed(bpm = 120, untilMillis = WARM_UP_OVER)

        assertEquals(9.5, run.last.targetSpeedKmh, DELTA)
    }

    @Test
    fun `an isolated spike does not move the speed on its own`() {
        val regulator = regulator(initialSpeedKmh = 9.0)
        regulator.feed(bpm = 150, untilMillis = WARM_UP_OVER)

        val outcome = regulator.update(
            nowElapsedMillis = WARM_UP_OVER + SAMPLE_PERIOD,
            sample = sample(bpm = 195, atMillis = WARM_UP_OVER + SAMPLE_PERIOD),
        )

        assertEquals(ZoneStatus.IN_ZONE, outcome.zone)
        assertFalse(outcome.speedChanged)
        assertEquals(9.0, outcome.targetSpeedKmh, DELTA)
    }

    @Test
    fun `a sustained rise does end up being followed`() {
        val regulator = regulator(initialSpeedKmh = 9.0)
        regulator.feed(bpm = 150, untilMillis = WARM_UP_OVER)

        val run = regulator.feed(
            bpm = 195,
            fromMillis = WARM_UP_OVER + SAMPLE_PERIOD,
            untilMillis = WARM_UP_OVER + 30_000L,
        )

        assertEquals(1, run.corrections)
        assertEquals(ZoneStatus.ABOVE, run.last.zone)
        assertEquals(8.5, run.last.targetSpeedKmh, DELTA)
    }

    @Test
    fun `a gentle setting corrects less and waits longer than a brisk one`() {
        assertTrue(
            RegulationResponsiveness.GENTLE.settleMillis >
                RegulationResponsiveness.BRISK.settleMillis
        )
        assertTrue(
            RegulationResponsiveness.GENTLE.speedStepKmh <
                RegulationResponsiveness.BRISK.speedStepKmh
        )
    }

    @Test
    fun `an unknown stored setting falls back on the default rather than failing`() {
        assertEquals(
            RegulationResponsiveness.DEFAULT,
            RegulationResponsiveness.fromName("SOMETHING_ELSE"),
        )
        assertEquals(RegulationResponsiveness.DEFAULT, RegulationResponsiveness.fromName(null))
    }

    @Test
    fun `the starting speed of a zone grows with the beats it asks for`() {
        val easy = HeartRateRegulator.startingSpeedKmh(
            target = HeartRateTarget(minBpm = 124, maxBpm = 143),
            speedRange = SPEED_RANGE,
        )
        val hard = HeartRateRegulator.startingSpeedKmh(
            target = HeartRateTarget(minBpm = 165, maxBpm = 175),
            speedRange = SPEED_RANGE,
        )

        assertTrue("$easy km/h should be a running pace", easy in 8.0..11.0)
        assertTrue("$hard km/h should be faster than $easy km/h", hard > easy)
    }

    @Test
    fun `a starting speed the treadmill cannot hold is brought back in range`() {
        val range = TreadmillCapabilities.ValueRange(
            minimum = 2.0,
            maximum = 8.0,
            increment = 0.5,
        )

        val speed = HeartRateRegulator.startingSpeedKmh(
            target = HeartRateTarget(minBpm = 175, maxBpm = 185),
            speedRange = range,
        )

        assertEquals(8.0, speed, DELTA)
    }

    private fun HeartRateRegulator.feed(
        bpm: Int,
        fromMillis: Long = 0L,
        untilMillis: Long,
    ): FeedResult {
        var outcome = update(fromMillis, sample(bpm, fromMillis))
        var corrections = if (outcome.speedChanged) 1 else 0
        var now = fromMillis + SAMPLE_PERIOD
        while (now <= untilMillis) {
            outcome = update(now, sample(bpm, now))
            if (outcome.speedChanged) corrections++
            now += SAMPLE_PERIOD
        }
        return FeedResult(last = outcome, corrections = corrections)
    }

    private data class FeedResult(val last: RegulationOutcome, val corrections: Int)

    private fun sample(bpm: Int, atMillis: Long) = HeartRateSample(
        measurement = HeartRateMeasurement(
            beatsPerMinute = bpm,
            sensorContact = HeartRateMeasurement.SensorContact.DETECTED,
            energyExpendedKiloJoules = null,
            rrIntervalsMillis = emptyList(),
        ),
        receivedAtElapsedMillis = atMillis,
    )

    private fun regulator(initialSpeedKmh: Double) = HeartRateRegulator(
        target = TARGET,
        speedRange = SPEED_RANGE,
        responsiveness = RegulationResponsiveness.NORMAL,
        initialSpeedKmh = initialSpeedKmh,
    )

    private companion object {
        const val DELTA = 0.0001
        const val SAMPLE_PERIOD = 1_000L

        val FIRST_CORRECTION_AT = HeartRateRegulator.WARM_UP_MILLIS
        val WARM_UP_OVER = FIRST_CORRECTION_AT + SAMPLE_PERIOD
        val SETTLE = RegulationResponsiveness.NORMAL.settleMillis
        val TARGET = HeartRateTarget(minBpm = 140, maxBpm = 160)
        val SPEED_RANGE = TreadmillCapabilities.ValueRange(
            minimum = 1.0,
            maximum = 16.0,
            increment = 0.1,
        )
    }
}
