package com.bitfire.uracer.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.bitfire.uracer.Art;
import com.bitfire.uracer.carsimulation.Replay;
import com.bitfire.uracer.effects.CarSkidMarks;
import com.bitfire.uracer.effects.SmokeTrails;
import com.bitfire.uracer.effects.TrackEffects;
import com.bitfire.uracer.effects.TrackEffects.Effects;
import com.bitfire.uracer.game.Game;
import com.bitfire.uracer.game.logic.DriftState;
import com.bitfire.uracer.game.logic.LapState;
import com.bitfire.uracer.messager.Messager;
import com.bitfire.uracer.utils.NumberString;

public class Hud {
	private Game game;

	private HudLabel best, curr, last;
	private Matrix4 topLeftOrigin, identity;
	private HudDebugMeter meterLatForce, meterSkidMarks, meterSmoke;

	// components
	private HudDrifting hudDrift;

	// effects
	public Hud( Game game ) {
		this.game = game;

		// y-flip
		topLeftOrigin = new Matrix4();
		topLeftOrigin.setToOrtho( 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), 0, 0, 10 );
		identity = new Matrix4();

		// grid-based position
		int gridX = (int)((float)Gdx.graphics.getWidth() / 5f);

		// laptimes component
		best = new HudLabel( Art.fontCurseYR, "BEST  TIME\n-.----" );
		curr = new HudLabel( Art.fontCurseYR, "YOUR  TIME\n-.----" );
		last = new HudLabel( Art.fontCurseYR, "LAST  TIME\n-.----" );

		// drifting component
		hudDrift = new HudDrifting( game );

		curr.setPosition( gridX, 50 );
		last.setPosition( gridX * 3, 50 );
		best.setPosition( gridX * 4, 50 );

		// meter lateral forces
		meterLatForce = new HudDebugMeter( game, 0, 100, 5 );
		meterLatForce.setLimits( 0, 1 );
		meterLatForce.setName( "lat-force-FRONT" );

		// meter skid marks count
		meterSkidMarks = new HudDebugMeter( game, 1, 100, 5 );
		meterSkidMarks.setLimits( 0, CarSkidMarks.MaxSkidMarks );
		meterSkidMarks.setName( "skid marks count" );

		meterSmoke = new HudDebugMeter( game, 2, 100, 5 );
		meterSmoke.setLimits( 0, SmokeTrails.MaxParticles );
		meterSmoke.setName( "smokepar count" );
	}

	public void dispose() {
	}

	public void tick(LapState lapState) {
		Messager.tick();
		hudDrift.tick();
		updateLapTimes( lapState );
	}

	private void updateLapTimes(LapState lapState) {

		// current time
		curr.setString( "YOUR  TIME\n" + NumberString.format( lapState.getElapsedSeconds() ) + "s" );

		// render best lap time
		Replay rbest = lapState.getBestReplay();

		// best time
		if( rbest != null && rbest.isValid ) {
			// has best
			best.setString( "BEST  TIME\n" + NumberString.format( rbest.trackTimeSeconds ) + "s" );
		}
		else {
			// temporarily use last track time
			if( lapState.hasLastTrackTimeSeconds() ) {
				best.setString( "BEST  TIME\n" + NumberString.format( lapState.getLastTrackTimeSeconds() ) + "s" );
			}
			else {
				best.setString( "BEST TIME\n-:----" );
			}
		}

		// last time
		if( lapState.hasLastTrackTimeSeconds() ) {
			// has only last
			last.setString( "LAST  TIME\n" + NumberString.format( lapState.getLastTrackTimeSeconds() ) + "s" );
		}
		else {
			last.setString( "LAST  TIME\n-:----" );
		}
	}

	public void render( SpriteBatch batch ) {
		batch.setTransformMatrix( identity );
		batch.setProjectionMatrix( topLeftOrigin );

		Gdx.gl.glActiveTexture( GL20.GL_TEXTURE0 );
		batch.begin();

		Messager.render( batch );

		curr.render( batch );
		best.render( batch );
		last.render( batch );

		// render drifting component
		hudDrift.render( batch );

		batch.end();
	}

	public void debug( SpriteBatch batch ) {
		DriftState drift = DriftState.get();

		// lateral forces
		meterLatForce.setValue( drift.driftStrength );

		if( drift.isDrifting )
			meterLatForce.color.set( .3f, 1f, .3f, 1f );
		else
			meterLatForce.color.set( 1f, 1f, 1f, 1f );

		meterLatForce.render( batch );

		meterSkidMarks.setValue( TrackEffects.getParticleCount( Effects.CarSkidMarks ) );
		meterSkidMarks.render( batch );

		meterSmoke.setValue( TrackEffects.getParticleCount( Effects.SmokeTrails ) );
		meterSmoke.render( batch );
	}

	/** Expose components
	 * TODO find a better way for this
	 *
	 * @return */

	public HudDrifting getDrifting() {
		return hudDrift;
	}
}
