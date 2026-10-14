import { useState } from "react";
import "./App.css";

type Policy = {
  id: string;
  client: string;
  type: string;
  status: string;
};

const policies: Policy[] = [
  { id: "POL-001", client: "Entreprise Alpha", type: "Santé", status: "Active" },
  { id: "POL-002", client: "Entreprise Beta", type: "Dentaire", status: "En attente" },
  { id: "POL-003", client: "Entreprise Gamma", type: "Prévoyance", status: "Active" },
];

const members = [
  { name: "Entreprise Alpha", role: "Assureur" },
  { name: "Entreprise Beta", role: "Client" },
  { name: "Entreprise Gamma", role: "Émetteur de polices" },
];

const claims = [
  { id: "SIN-001", client: "Entreprise Alpha", status: "En cours" },
  { id: "SIN-002", client: "Entreprise Beta", status: "À examiner" },
  { id: "SIN-003", client: "Entreprise Gamma", status: "Clôturé" },
];

function App() {
  const [page, setPage] = useState("Dashboard");
  const [search, setSearch] = useState("");

  const navigation = ["Dashboard", "Polices", "Membres", "Sinistres"];

  const filteredPolicies = policies.filter((policy) =>
    `${policy.id} ${policy.client} ${policy.type}`
      .toLowerCase()
      .includes(search.toLowerCase())
  );

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">IN</div>
          <div>
            <strong>Insurance Network</strong>
            <small>Business Platform</small>
          </div>
        </div>

        <p className="nav-label">MENU PRINCIPAL</p>

        <nav>
          {navigation.map((item) => (
            <button
              key={item}
              className={`nav-item ${page === item ? "selected" : ""}`}
              onClick={() => {
                setPage(item);
                setSearch("");
              }}
            >
              <span>{{
                Dashboard: "▦",
                Polices: "▤",
                Membres: "♙",
                Sinistres: "□",
              }[item]}</span>
              {item}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <span className="status-dot" />
          Réseau d'assurance
          <small>Interface de démonstration</small>
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <p className="breadcrumb">Insurance Network / {page}</p>
            <h1>{page === "Dashboard" ? "Tableau de bord" : page}</h1>
          </div>
          <div className="profile">
            <div className="avatar">AD</div>
            <div>
              <strong>Administrateur</strong>
              <small>Gestionnaire du réseau</small>
            </div>
          </div>
        </header>

        {page === "Dashboard" && (
          <>
            <section className="welcome">
              <div>
                <p className="eyebrow">ESPACE DE GESTION</p>
                <h2>Bienvenue sur votre espace assurance</h2>
                <p>
                  Retrouvez les polices, les membres et les sinistres
                  de votre réseau au même endroit.
                </p>
              </div>
              <div className="welcome-mark">IN</div>
            </section>

            <section className="stats">
              <Stat title="Polices d'assurance" value="248" detail="Total de démonstration" />
              <Stat title="Membres du réseau" value="36" detail="Total de démonstration" />
              <Stat title="Sinistres en cours" value="12" detail="Total de démonstration" />
              <Stat title="Polices actives" value="192" detail="Total de démonstration" />
            </section>

            <Panel title="Dernières polices" action={() => setPage("Polices")}>
              <PolicyTable data={policies.slice(0, 3)} />
            </Panel>

            <section className="bottom-grid">
              <Panel title="Membres récents" action={() => setPage("Membres")}>
                {members.slice(0, 2).map((member) => (
                  <div className="list-row" key={member.name}>
                    <div className="avatar">{member.name.slice(0, 2).toUpperCase()}</div>
                    <div className="list-info">
                      <strong>{member.name}</strong>
                      <small>{member.role}</small>
                    </div>
                  </div>
                ))}
              </Panel>

              <Panel title="Sinistres récents" action={() => setPage("Sinistres")}>
                {claims.slice(0, 2).map((claim) => (
                  <div className="list-row" key={claim.id}>
                    <div className="list-info">
                      <strong>{claim.id}</strong>
                      <small>{claim.client}</small>
                    </div>
                    <span className="badge">{claim.status}</span>
                  </div>
                ))}
              </Panel>
            </section>
          </>
        )}

        {page === "Polices" && (
          <Panel title="Gestion des polices">
            <Search value={search} onChange={setSearch} />
            <PolicyTable data={filteredPolicies} />
            {filteredPolicies.length === 0 && (
              <p className="empty">Aucune police trouvée.</p>
            )}
          </Panel>
        )}

        {page === "Membres" && (
          <Panel title="Membres du réseau">
            {members.map((member) => (
              <div className="list-row" key={member.name}>
                <div className="avatar">{member.name.slice(0, 2).toUpperCase()}</div>
                <div className="list-info">
                  <strong>{member.name}</strong>
                  <small>{member.role}</small>
                </div>
                <span className="badge">Enregistré</span>
              </div>
            ))}
          </Panel>
        )}

        {page === "Sinistres" && (
          <Panel title="Gestion des sinistres">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>Référence</th><th>Client</th><th>Statut</th></tr>
                </thead>
                <tbody>
                  {claims.map((claim) => (
                    <tr key={claim.id}>
                      <td><strong>{claim.id}</strong></td>
                      <td>{claim.client}</td>
                      <td><span className="badge">{claim.status}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Panel>
        )}

        <footer>
          Insurance Business Network <span>•</span> Prototype frontend
        </footer>
      </main>
    </div>
  );
}

function Stat({ title, value, detail }: { title: string; value: string; detail: string }) {
  return (
    <article className="stat-card">
      <p>{title}</p>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

function Panel({
  title,
  action,
  children,
}: {
  title: string;
  action?: () => void;
  children: React.ReactNode;
}) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <h3>{title}</h3>
        {action && <button className="text-button" onClick={action}>Voir tout →</button>}
      </div>
      {children}
    </section>
  );
}

function Search({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <input
      className="search"
      placeholder="Rechercher une police..."
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

function PolicyTable({ data }: { data: Policy[] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr><th>Référence</th><th>Client</th><th>Type</th><th>Statut</th></tr>
        </thead>
        <tbody>
          {data.map((policy) => (
            <tr key={policy.id}>
              <td><strong>{policy.id}</strong></td>
              <td>{policy.client}</td>
              <td>{policy.type}</td>
              <td><span className="badge">{policy.status}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default App; 