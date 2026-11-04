import React from 'react';
import './LegalPage.css';

/** Plain document page: a title, a last-updated line and numbered sections of paragraphs. */
const LegalPage = ({ title, updated, intro, sections }) => (
  <div className="legal-page">
    <article className="legal-card">
      <h1>{title}</h1>
      <p className="legal-updated">Cập nhật lần cuối: {updated}</p>
      {intro && <p>{intro}</p>}
      {sections.map((section, index) => (
        <section key={section.title}>
          <h2>
            {index + 1}. {section.title}
          </h2>
          {section.paragraphs.map((text) => (
            <p key={text}>{text}</p>
          ))}
        </section>
      ))}
    </article>
  </div>
);

export default LegalPage;
