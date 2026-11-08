# App Finanze

App web per il telefono che gestisce il budget giornaliero partendo dallo stipendio.

- **Budget giornaliero dinamico:** (stipendio + entrate extra − spese fisse − accantonamenti − obiettivo di risparmio) ÷ giorni fino al 21 del mese successivo. Si ricalcola ogni giorno.
- **Spese fisse** mensili (finanziamento, PAC, mutuo…), che si possono aggiungere, mettere in pausa ed eliminare.
- **Scadenze annuali** (bollo, assicurazione) con accantonamento automatico a ogni stipendio.
- **Entrate extra** (trasferte, lavori extra).
- **Previsione carburante** basata sui rifornimenti registrati.
- **Collegamento con MacroDroid** tramite link (`?titolo=…&notifica=…&fonte=wallet`, `?add=3.50`, `?voce=…`, `?stipendio=…`).
- **Doppioni:** non registra due volte un pagamento notificato sia da Wallet sia dalla banca.

I dati restano solo sul telefono (localStorage). Per non perderli si usa Impostazioni → Backup.
