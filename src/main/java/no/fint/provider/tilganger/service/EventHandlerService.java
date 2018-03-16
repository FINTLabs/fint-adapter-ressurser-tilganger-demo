package no.fint.provider.tilganger.service;

import lombok.extern.slf4j.Slf4j;
import no.fint.event.model.Event;
import no.fint.event.model.Status;
import no.fint.event.model.health.Health;
import no.fint.event.model.health.HealthStatus;
import no.fint.model.administrasjon.personal.Personalressurs;
import no.fint.model.felles.kompleksedatatyper.Identifikator;
import no.fint.model.relation.FintResource;
import no.fint.model.relation.Relation;
import no.fint.model.ressurser.tilganger.Identitet;
import no.fint.model.ressurser.tilganger.Rettighet;
import no.fint.model.ressurser.tilganger.TilgangerActions;
import no.fint.provider.adapter.event.EventResponseService;
import no.fint.provider.adapter.event.EventStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The EventHandlerService receives the <code>event</code> from SSE endpoint (provider) in the {@link #handleEvent(Event)} method.
 */
@Slf4j
@Service
public class EventHandlerService {

    @Autowired
    private EventResponseService eventResponseService;

    @Autowired
    private EventStatusService eventStatusService;

    /**
     * <p>
     * HandleEvent is responsible of handling the <code>event</code>. This is what should be done:
     * </p>
     * <ol>
     * <li>Verify that the adapter can handle the <code>event</code>. This is done in the {@link EventStatusService#verifyEvent(Event)} method</li>
     * <li>Call the code to handle the action</li>
     * <li>Posting back the handled <code>event</code>. This done in the {@link EventResponseService#postResponse(Event)} method</li>
     * </ol>
     * <p>
     * This is where you implement your code for handling the <code>event</code>. It is typically done by making a onEvent method:
     * </p>
     * <pre>
     *     {@code
     *     public void onGetAllIdentitet(Event<FintResource> dogAllEvent) {
     *
     *         // Call a service to get all dogs from the application and add the result to the event data
     *         // dogAllEvent.addData(dogResource);
     *
     *     }
     *     }
     * </pre>
     *
     * @param event The <code>event</code> received from the provider
     */
    public void handleEvent(Event event) {
        if (event.isHealthCheck()) {
            postHealthCheckResponse(event);
        } else {
            if (event != null && eventStatusService.verifyEvent(event).getStatus() == Status.ADAPTER_ACCEPTED) {
                TilgangerActions action = TilgangerActions.valueOf(event.getAction());
                Event<FintResource> responseEvent = new Event<>(event);

                responseEvent.setStatus(Status.ADAPTER_RESPONSE);
                switch (action) {
                    case GET_ALL_IDENTITET:
                        onGetAllIdentitet(responseEvent);
                        break;
                    case GET_ALL_RETTIGHET:
                        onGetAllRettighet(responseEvent);
                        break;
                    default:
                        responseEvent.setStatus(Status.ADAPTER_REJECTED);
                }

                eventResponseService.postResponse(responseEvent);
            }
        }
    }


    /**
     * Example of handling action
     *
     * @param responseEvent Event containing the response
     */
    private void onGetAllRettighet(Event<FintResource> responseEvent) {
        Identifikator batcaveId = new Identifikator();
        batcaveId.setIdentifikatorverdi("BATCAVE");
        Rettighet batcave = new Rettighet();
        batcave.setSystemId(batcaveId);
        batcave.setNavn("Batcave");
        batcave.setKode("BAT-002");
        batcave.setBeskrivelse("Grants access to the secret cave");
        responseEvent.addData(FintResource
                .with(batcave)
                .addRelations(new Relation.Builder().with(Rettighet.Relasjonsnavn.IDENTITET).forType(Identitet.class).field("systemid").value("BATMAN").build())
                .addRelations(new Relation.Builder().with(Rettighet.Relasjonsnavn.IDENTITET).forType(Identitet.class).field("systemid").value("ROBIN").build())
        );

        Identifikator batmobileId = new Identifikator();
        batmobileId.setIdentifikatorverdi("BATMOBILE");
        Rettighet batmobile = new Rettighet();
        batmobile.setSystemId(batmobileId);
        batmobile.setKode("BAT-001");
        batmobile.setNavn("Batmobile");
        batmobile.setBeskrivelse("Grants access to driving the ultimate vehicle");
        responseEvent.addData(FintResource.with(batmobile)
                .addRelations(new Relation.Builder().with(Rettighet.Relasjonsnavn.IDENTITET).forType(Identitet.class).field("systemid").value("BATMAN").build())
        );
    }

    /**
     * Example of handling action
     *
     * @param responseEvent Event containing the response
     */
    private void onGetAllIdentitet(Event<FintResource> responseEvent) {
        Identifikator batmanId = new Identifikator();
        batmanId.setIdentifikatorverdi("BATMAN");
        Identitet batman = new Identitet();
        batman.setSystemId(batmanId);
        responseEvent.addData(FintResource
                .with(batman)
                .addRelations(new Relation.Builder().with(Identitet.Relasjonsnavn.PERSONALRESSURS).forType(Personalressurs.class).field("ansattnummer").value("100001").build())
                .addRelations(new Relation.Builder().with(Identitet.Relasjonsnavn.RETTIGHET).forType(Rettighet.class).field("systemid").value("BATCAVE").build())
                .addRelations(new Relation.Builder().with(Identitet.Relasjonsnavn.RETTIGHET).forType(Rettighet.class).field("systemid").value("BATMOBILE").build()));

        Identifikator robinId = new Identifikator();
        robinId.setIdentifikatorverdi("ROBIN");
        Identitet robin = new Identitet();
        robin.setSystemId(robinId);
        responseEvent.addData(FintResource
                .with(robin)
                .addRelations(new Relation.Builder().with(Identitet.Relasjonsnavn.PERSONALRESSURS).forType(Personalressurs.class).field("ansattnummer").value("100002").build())
                .addRelations(new Relation.Builder().with(Identitet.Relasjonsnavn.RETTIGHET).forType(Rettighet.class).field("systemid").value("BATCAVE").build()));
    }

    /**
     * Checks if the application is healthy and updates the event object.
     *
     * @param event The event object
     */
    public void postHealthCheckResponse(Event event) {
        Event<Health> healthCheckEvent = new Event<>(event);
        healthCheckEvent.setStatus(Status.TEMP_UPSTREAM_QUEUE);

        if (healthCheck()) {
            healthCheckEvent.addData(new Health("adapter", HealthStatus.APPLICATION_HEALTHY.name()));
        } else {
            healthCheckEvent.addData(new Health("adapter", HealthStatus.APPLICATION_UNHEALTHY));
            healthCheckEvent.setMessage("The adapter is unable to communicate with the application.");
        }

        eventResponseService.postResponse(healthCheckEvent);
    }

    /**
     * This is where we implement the health check code
     *
     * @return {@code true} if health is ok, else {@code false}
     */
    private boolean healthCheck() {
        log.info("\n" +
                "                T\\ T\\\n" +
                "                | \\| \\\n" +
                "                |  |  :\n" +
                "           _____I__I  |\n" +
                "         .'            '.\n" +
                "       .'                '\n" +
                "       |   ..             '\n" +
                "       |  /__.            |\n" +
                "       :.' -'             |\n" +
                "      /__.                |\n" +
                "     /__, \\               |\n" +
                "        |__\\        _|    |\n" +
                "        :  '\\     .'|     |\n" +
                "        |___|_,,,/  |     |    _..--.\n" +
                "     ,--_-   |     /'      \\../ /  /\\\\\n" +
                "    ,'|_ I---|    7    ,,,_/ / ,  / _\\\\\n" +
                "  ,-- 7 \\|  / ___..,,/   /  ,  ,_/   '-----.\n" +
                " /   ,   \\  |/  ,____,,,__,,__/            '\\\n" +
                ",   ,     \\__,,/                             |\n" +
                "| '.       _..---.._                         !.\n" +
                "! |      .' z_M__s. '.                        |\n" +
                ".:'      | (-_ _--')  :          L            !\n" +
                ".'.       '.  Y    _.'             \\,         :\n" +
                " .          '-----'                 !          .\n" +
                " .           /  \\                   .          .");
        /*
         * Check application connectivity etc.
         */
        return true;
    }

}
