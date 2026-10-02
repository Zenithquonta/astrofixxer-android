package org.astrofixxer.ui

import org.astrofixxer.astro.Mounting
import org.astrofixxer.astro.PhoneAxis
import org.astrofixxer.astro.ViewGuess

// The setup vocabulary lives with the maths in astro/ so it can be tested there; the UI uses the same names.
typealias TelescopeType = org.astrofixxer.astro.TelescopeType
typealias PhonePlacement = org.astrofixxer.astro.PhonePlacement
typealias PhoneEdge = org.astrofixxer.astro.PhoneEdge
typealias EyepieceAngle = org.astrofixxer.astro.EyepieceAngle
typealias Erecting = org.astrofixxer.astro.Erecting

/** What kind of mount carries the telescope. Equatorial mounts get guidance in RA and Dec. */
enum class MountType { ALT_AZ, EQUATORIAL, OTHER }

/**
 * How the telescope and phone are set up. [placement], [edge] and [eyepieceAngle] decide which way the phone's sensors
 * point along the telescope, so changing them breaks an alignment. The rest never does: [type], [mount], [erecting]
 * and the eyepiece view ([viewRotationDeg] 0/90/180/270, [viewMirrored]) only change drawings and first guesses.
 * The defaults are the app's original behaviour: phone flat on the tube, top edge toward the front.
 * A Newtonian's eyepiece is always at a right angle: whatever [eyepieceAngle] holds, [effectiveEyepieceAngle] is what
 * counts, and everything that reads the angle (axis, view guess, [mountingDiffers], the texts, saved settings) uses it.
 */
data class TelescopeSetup(
    val type: TelescopeType = TelescopeType.OTHER,
    val mount: MountType = MountType.ALT_AZ,
    val placement: PhonePlacement = PhonePlacement.TUBE,
    val edge: PhoneEdge = PhoneEdge.TOP,
    val eyepieceAngle: EyepieceAngle = EyepieceAngle.UNSURE,
    val erecting: Erecting = Erecting.UNSURE,
    val viewRotationDeg: Int = 0,
    val viewMirrored: Boolean = false,
) {
    /** The eyepiece angle that applies: always a right angle on a reflector, otherwise [eyepieceAngle]. */
    val effectiveEyepieceAngle: EyepieceAngle get() = Mounting.effectiveEyepieceAngle(type, eyepieceAngle)

    /** The same setup with [eyepieceAngle] set to [effectiveEyepieceAngle]; used when loading saved settings and when the wizard finishes. */
    fun normalised(): TelescopeSetup = if (eyepieceAngle == effectiveEyepieceAngle) this else copy(eyepieceAngle = effectiveEyepieceAngle)

    /** The telescope axis in phone coordinates for this setup. */
    fun axis(): PhoneAxis = Mounting.phoneAxis(type, placement, edge, eyepieceAngle)

    /** A first guess at the eyepiece view for this setup's optics. */
    fun viewGuess(): ViewGuess = Mounting.initialViewOrientation(type, placement, eyepieceAngle, erecting)

    /** The same setup with the eyepiece view set to the first guess. */
    fun withGuessedView(): TelescopeSetup = viewGuess().let { copy(viewRotationDeg = it.orientation.rotationDeg, viewMirrored = it.orientation.mirrored) }

    /**
     * True when the two setups differ in something that changes which way the phone points along the telescope. The
     * eyepiece angle only counts on the eyepiece, and as [effectiveEyepieceAngle]: choosing a Newtonian while the phone
     * sat on a straight eyepiece changes the axis, while the type alone on the tube does not.
     */
    fun mountingDiffers(other: TelescopeSetup) = placement != other.placement || edge != other.edge ||
        (placement == PhonePlacement.EYEPIECE && effectiveEyepieceAngle != other.effectiveEyepieceAngle)
}
