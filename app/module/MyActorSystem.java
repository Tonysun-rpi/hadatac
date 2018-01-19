package module;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.hadatac.data.loader.AnnotationWorker;

import akka.actor.ActorSystem;
import play.libs.Akka;
import scala.concurrent.duration.FiniteDuration;

@Singleton
public class MyActorSystem {

	@Inject
	private ActorSystem system;

	@Inject
	public MyActorSystem(ActorSystem system) {
		this.system = system;
		schedule();
	}

	public void schedule() {
		// Create thread for auto csv annotation
		FiniteDuration delay = FiniteDuration.create(0, TimeUnit.SECONDS);
		FiniteDuration frequency = FiniteDuration.create(15, TimeUnit.SECONDS);

		Runnable annotation = new Runnable() {
			@Override
			public void run() {
				AnnotationWorker.autoAnnotate();
			}
		};

		system.scheduler().schedule(delay, frequency, annotation, system.dispatcher());
	}
}
