package com.bitfire.uracer.game.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.WindowedMean;
import com.bitfire.uracer.configuration.Config;
import com.bitfire.uracer.game.actors.Car;
import com.bitfire.uracer.game.actors.CarDescriptor;
import com.bitfire.uracer.game.actors.CarForces;
import com.bitfire.uracer.game.actors.CarModel;
import com.bitfire.uracer.game.actors.CarState;
import com.bitfire.uracer.game.actors.CarType;
import com.bitfire.uracer.game.logic.Input;
import com.bitfire.uracer.game.rendering.GameRenderer;
import com.bitfire.uracer.game.rendering.GameRendererEvent;
import com.bitfire.uracer.game.world.GameWorld;
import com.bitfire.uracer.utils.AMath;
import com.bitfire.uracer.utils.VMath;

public class PlayerCar extends Car {

	// car forces simulator
	private CarSimulator carSim = null;
	private CarDescriptor carDesc = null;

	// input
	private Input input = null;
	private CarInput carInput = null;
	private float lastTouchAngle;
	private Vector2 touchPos = new Vector2();
	private Vector2 carPos = new Vector2();
	private float invWidth = 1f / Gdx.graphics.getWidth(), invHeight = 1f / Gdx.graphics.getHeight();
	private WindowedMean frictionMean = new WindowedMean( 10 );

	// damping values
	private float dampLinearVelocityAF = 0;
	private float dampThrottleAF = 0;
	private float dampFriction = 0;

	// states
	public CarState carState = null;
	public PlayerDriftState driftState = null;

	public PlayerCar( GameWorld gameWorld, CarModel model, Aspect aspect ) {
		super( gameWorld, CarType.PlayerCar, InputMode.InputFromPlayer, GameRendererEvent.Order.DEFAULT, model, aspect, true );
		carInput = new CarInput();
		impacts = 0;

		carDesc = new CarDescriptor();
		carDesc.carModel.set( model );
		carSim = new CarSimulator( carDesc );
		renderer.setAlpha( 1 );

		// states
		this.carState = new CarState( gameWorld, this );
		this.driftState = new PlayerDriftState( this );
	}

	@Override
	public void dispose() {
		super.dispose();
		driftState.dispose();
	}

	// use strictly for debug purposes *ONLY*!
	public CarDescriptor getCarDescriptor() {
		return carDesc;
	}

	@Override
	public void reset() {
		super.reset();
		frictionMean.clear();
		carState.reset();
		driftState.reset();
	}

	@Override
	protected void resetPhysics() {
		super.resetPhysics();
		carSim.resetPhysics();
	}

	/** Sets the input system this PlayerCar will use to check for input events */
	public void setInputSystem( Input input ) {
		this.input = input;
	}

	/** After processing collision's feedback this damping will be applied
	 * to the car's linear velocity. */
	public void setDampingLinearVelocityAF( float damping ) {
		dampLinearVelocityAF = damping;
	}

	/** After processing collision's feedback this damping will be applied
	 * to the car's input throttle */
	public void setDampingThrottleAF( float damping ) {
		dampThrottleAF = damping;
	}

	/** When the player's car is off-track this damping will be applied
	 * to the car's linear velocity */
	public void setDampingFriction( float damping ) {
		dampFriction = damping;
	}

	public void setFriction( float value ) {
		frictionMean.addValue( value );
	}

	protected CarInput acquireInput() {
		if( input == null ) {
			carInput.reset();
			return carInput;
		}

		carPos.set( GameRenderer.ScreenUtils.screenPosForMt( body.getPosition() ) );

//		touchPos.set( Gdx.input.getX(), Gdx.input.getY() );
//		carInput.updated = Gdx.input.isTouched();
		touchPos.set( input.getXY() );
		carInput.updated = input.isTouching();

		if( carInput.updated ) {
			float angle = 0;

			// avoid singularity
			if( (int)-carPos.y + (int)touchPos.y == 0 ) {
				angle = lastTouchAngle;
			} else {
				angle = MathUtils.atan2( -carPos.x + touchPos.x, -carPos.y + touchPos.y );
				lastTouchAngle = angle;
			}

			float wrapped = -body.getAngle();

			angle -= AMath.PI;
			angle += wrapped; // to local
			if( angle < 0 ) {
				angle += AMath.TWO_PI;
			}

			angle = -(angle - AMath.TWO_PI);
			if( angle > AMath.PI ) {
				angle = angle - AMath.TWO_PI;
			}

			carInput.steerAngle = angle;

			// normalize and clamp
			touchPos.x *= invWidth;
			touchPos.y *= invHeight;
			carPos.x *= invWidth;
			carPos.y *= invHeight;
			VMath.clamp( touchPos, 0, 1 );
			VMath.clamp( carPos, 0, 1 );

			// compute throttle
			carInput.throttle = touchPos.dst( carPos ) * 4 * carDesc.carModel.max_force;
			// carInput.throttle = touchPos.dst( carPos ) * 2 * carDesc.carModel.max_force; // x2 = 0<->halfscreen is
			// considered 0<->1
		}

		return carInput;
	}

	private void applyFriction() {
		// FIXME, move these hard-coded values out of here
		if( frictionMean.getMean() < -0.1 && carDesc.velocity_wc.len2() > 10 ) {
			carDesc.velocity_wc.mul( dampFriction );
		}
	}

	@Override
	protected void onComputeCarForces( CarForces forces ) {
		carInput = acquireInput();

		if( Config.Debug.ApplyCarFrictionFromMap ) {
			applyFriction();
		}

		// handle decrease queued from previous step
		handleDecrease( carInput );

		carSim.applyInput( carInput );
		carSim.step( Config.Physics.PhysicsDt, body.getAngle() );

		// update computed forces
		forces.velocity_x = carDesc.velocity_wc.x;
		forces.velocity_y = carDesc.velocity_wc.y;
		forces.angularVelocity = carDesc.angularvelocity;

		// update the car descriptor (car simulator data) with newly computed forces
		// (no interface on carsim for performance reasons!)
		carDesc.velocity_wc.set( forces.velocity_x, forces.velocity_y );
		carDesc.angularvelocity = forces.angularVelocity;
	}

	@Override
	public void onAfterPhysicsSubstep() {
		super.onAfterPhysicsSubstep();

		// inspect impact feedback, accumulate vel/ang velocities
		handleImpactFeedback();
	}

	@Override
	public void onTemporalAliasing( boolean stepped, float aliasingFactor ) {
		super.onTemporalAliasing( stepped, aliasingFactor );

		// update the states once ticking has finished, if any
		carState.update( carDesc );
		driftState.update( carSim.lateralForceFront.y, carSim.lateralForceRear.y, carDesc.velocity_wc.len() );
	}

	private long start_timer = 0;
	private boolean start_decrease = false;

	private void handleImpactFeedback() {
		// process impact feedback
		while( impacts > 0 ) {
			impacts--;
			carDesc.velocity_wc.set( body.getLinearVelocity() ).mul( dampLinearVelocityAF );
			carDesc.angularvelocity = -body.getAngularVelocity() * 0.85f;
			start_decrease = true;
		}
	}

	private void handleDecrease( CarInput input ) {
		if( start_decrease || (System.nanoTime() - start_timer < 250000000L) ) {
			if( start_decrease ) {
				start_decrease = false;
				start_timer = System.nanoTime();
			}

			input.throttle *= dampThrottleAF;
		}
	}
}
