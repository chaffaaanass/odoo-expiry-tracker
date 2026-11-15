package io.github.chaffaaanass.expirytracker.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OdooSessionData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String baseUrl;
    private String database;
    private String username;

    private String password;

    /** Odoo uid returned by /web/session/authenticate */
    private Integer uid;

    /** Odoo session_id cookie – sent to Odoo on every XML-RPC call */
    private String odooSessionId;
}
