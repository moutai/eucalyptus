package com.eucalyptus.util.fsm;

import com.eucalyptus.util.HasName;

/**
 * <p>
 * A callback interface which is invoked according to the progress of a
 * transitions between two states: {@fromState} is the state before
 * the transition and {@toState} is the state after the transition. The primary
 * purpose of implementing classes is to enforce pre-conditions and apply side
 * effects corresponding with the change of state. The expectation is that
 * transitions are applied atomically in the sense that only one can be in
 * progress at a time.
 * </p>
 * <p>
 * The methods are invoked in the following order; their purpose is summarized
 * here with details in the documentation for each method:
 * <ol>
 * <li>{@link #before()}: prior to any change of state -- check
 * preconditions.</li>
 * <li>{@link #leave()}: on leaving {@code fromState}.</li>
 * <li>{@link #enter()}: on entering {@code toState}.</li>
 * <li>
 * <li>{@link #after(toState)}: after the transition has been completed.</li>
 * </ol>
 * </p>
 * @author decker
 * 
 * @param <S> enum type of the states in the state machine.
 */
public interface TransitionListener<P extends HasName<P>> {
  public enum Phases { before, leave, enter, after }
  
  /**
   * Invoked before leaving the {@code fromState}. At this time the transition
   * has not yet begun and can be aborted. A return of false or a caught
   * exception will stop application of the transition.
   * 
   * Implementors should ensure to avoid side-effects.
   * 
   * @return false iff the transition should not be executed.
   */
  public abstract boolean before( P parent );
  
  /**
   * Applies changes corresponding with leaving {@code fromState} and is invoked
   * when the transition begins and after the state has been changed to
   * {@code toState} but before any side effects have been applied.
   * 
   * Implementors can assume that no other transitions will execute until this
   * transition has completed.
   */
  public abstract void leave( P parent );
  
  /**
   * Applies changes corresponding with having entered {@code toState} and is
   * invoked when the transition completes leaving the state as {@code toState}.
   * 
   * Implementors can assume that no other transitions will execute until this
   * transition has completed.
   */
  public abstract void enter( P parent );
  
  /**
   * Invoked after the transition completes but before any other transition is
   * evaluated against the underlying state machine.
   * 
   * Implementors should ensure to avoid side-effects.
   */
  public abstract void after( P parent );

}
