package com.bitfire.uracer.audio;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.bitfire.uracer.Config;
import com.bitfire.uracer.events.DriftStateListener;
import com.bitfire.uracer.game.GameData;
import com.bitfire.uracer.utils.AMath;

public class CarDriftSoundEffect extends CarSoundEffect implements DriftStateListener {
	private Sound drift = null;
	private long driftId = -1;
	private float driftLastPitch = 0;
	private final float pitchFactor = 1f;
	private final float pitchMin = 0.75f;
	private final float pitchMax = 1f;

	private boolean doFadeIn = false;
	private boolean doFadeOut = false;
	private float lastVolume = 0f;

	public CarDriftSoundEffect() {
		GameData.driftState.addListener( this );
		drift = Gdx.audio.newSound( Gdx.files.getFileHandle( "data/audio/drift-loop.ogg", FileType.Internal ) );
	}

	@Override
	public void dispose() {
		drift.stop();
		drift.dispose();
	}

	@Override
	public void start() {
		// UGLY HACK FOR ANDROID
		if( Config.isDesktop )
			driftId = drift.loop( 0f );
		else
			driftId = checkedLoop( drift, 0f );

		drift.setPitch( driftId, pitchMin );
		drift.setVolume( driftId, 0f );
	}

	@Override
	public void stop() {
		if( driftId > -1 ) {
			drift.stop( driftId );
		}

		doFadeIn = doFadeOut = false;
	}

	@Override
	public void reset() {
		stop();
		lastVolume = 0;
	}

	@Override
	public void onBeginDrift() {
		if( driftId > -1 ) {
			drift.stop( driftId );
			driftId = drift.loop( 0f );
			drift.setVolume( driftId, 0f );
		}

		doFadeIn = true;
		doFadeOut = false;
	}

	@Override
	public void onEndDrift() {
		doFadeIn = false;
		doFadeOut = true;
	}

	public void update( float speedFactor ) {
		if( driftId > -1 ) {
			float pitch = speedFactor * pitchFactor + pitchMin;

			// apply time factor
			// pitch *= URacer.timeMultiplier;

			// System.out.println(URacer.timeMultiplier);

			pitch = AMath.clamp( pitch, pitchMin, pitchMax );

			if( !AMath.equals( pitch, driftLastPitch ) ) {
				drift.setPitch( driftId, pitch );
				driftLastPitch = pitch;
			}

			// modulate volume
			if( doFadeIn ) {
				if( lastVolume < 1f )
					lastVolume += 0.01f;
				else {
					lastVolume = 1f;
					doFadeIn = false;
				}
			} else if( doFadeOut ) {
				if( lastVolume > 0f )
					lastVolume -= 0.03f;
				else {
					lastVolume = 0f;
					doFadeOut = false;
				}
			}

			lastVolume = AMath.clamp( lastVolume, 0, 1f );
			drift.setVolume( driftId, GameData.driftState.driftStrength * lastVolume );
		}

	}
}
