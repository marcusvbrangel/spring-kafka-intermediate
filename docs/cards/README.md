# Anki Flashcards - Inglês para Desenvolvimento de Software

## 📚 Descrição

Esta coleção contém **1535 flashcards** em formato CSV para praticar inglês no contexto de desenvolvimento de software, com foco em:
- Desenvolvimento backend (Java, Spring, APIs)
- DevOps (Docker, Kubernetes, CI/CD)
- Scrum e Agile
- Comunicação e reuniões
- Problemas do dia a dia (bugs, outages, deploys)
- Phrasal verbs aplicados a software
- Documentação e Pull Requests
- Entrevistas técnicas

## 📁 Arquivos

### Parte 1: Desenvolvimento e Infraestrutura (1017 cards)

1. **01_backend_development.csv** (201 cards)
   - REST APIs, Spring Boot, JPA/Hibernate
   - Validação, exception handling, DTOs
   - Kafka, mensageria
   - Performance, caching, otimização
   - Segurança (JWT, OAuth, authentication)
   - Testing (JUnit, Mockito, TestContainers)
   - Monitoramento e logging

2. **02_devops_infrastructure.csv** (198 cards)
   - Docker e containers
   - Kubernetes (pods, deployments, services)
   - CI/CD pipelines
   - Monitoring (Prometheus, Grafana)
   - Terraform, Infrastructure as Code
   - Backups e disaster recovery
   - Networking e segurança

3. **03_scrum_agile.csv** (195 cards)
   - Daily standup
   - Sprint planning, review, retrospective
   - Backlog refinement
   - User stories, story points
   - Definition of Done
   - Agile values e princípios

4. **04_communication_meetings.csv** (204 cards)
   - Comunicação de equipe (Slack, email)
   - Reuniões e calls
   - Feedback e reconhecimento
   - Negociação e tomada de decisão
   - Updates de status

5. **05_phrasal_verbs.csv** (117 cards)
   - set up, roll out, roll back
   - break down, figure out, look into
   - scale up/down, bring up/down
   - track down, narrow down
   - E muito mais!

6. **06_documentation_prs.csv** (102 cards)
   - Pull requests e code review
   - Git (merge, rebase, conflicts)
   - Commit messages
   - API documentation (Swagger, OpenAPI)
   - READMEs e changelogs
   - Versioning e releases

### Parte 2: Entrevistas Técnicas (518 cards)

7. **07_tech_interviews.csv** (518 cards)
   - Perguntas comportamentais
   - Experiência e background
   - Perguntas técnicas (Java, Spring, databases)
   - Design patterns e SOLID
   - Estruturas de dados e algoritmos
   - System design
   - Perguntas sobre a empresa/role

## 🎯 Como Importar no Anki

### Passo 1: Abrir o Anki
1. Abra o Anki Desktop
2. Clique em **File** > **Import**

### Passo 2: Importar o CSV
1. Selecione o arquivo CSV que deseja importar
2. Configure as opções de importação:
   - **Type**: Basic
   - **Deck**: Escolha ou crie um deck (ex: "Inglês Tech")
   - **Field separator**: Semicolon (;)
   - **Allow HTML in fields**: Marcado

### Passo 3: Mapear os Campos
1. Field 1 → Front
2. Field 2 → Back

### Passo 4: Finalizar
1. Clique em **Import**
2. Repita para cada arquivo CSV

## 🔊 Ativando TTS (Text-to-Speech)

O Anki possui TTS nativo que funciona automaticamente. Para configurar:

1. Vá em **Tools** > **Manage Note Types**
2. Selecione seu tipo de card (Basic)
3. Clique em **Cards**
4. No template do Front, adicione:
   ```
   {{Front}}
   {{tts en_US:Front}}
   ```
5. No template do Back, adicione:
   ```
   {{FrontSide}}
   <hr id=answer>
   {{Back}}
   {{tts pt_BR:Back}}
   ```

Agora o Anki lerá automaticamente em inglês (Front) e português (Back).

## 💡 Dicas de Uso

1. **Comece devagar**: Importe 1-2 arquivos por vez
2. **Estude regularmente**: 20-30 cards por dia são melhores que 200 de uma vez
3. **Use o TTS**: Ouça a pronúncia para melhorar listening
4. **Contextualize**: Tente usar as frases em conversas reais
5. **Revise**: O algoritmo do Anki funciona melhor com revisões diárias

## 📊 Estatísticas

| Arquivo | Cards | Foco |
|---------|-------|------|
| Backend Development | 201 | Java, Spring, APIs, Testing |
| DevOps/Infrastructure | 198 | Docker, K8s, CI/CD |
| Scrum/Agile | 195 | Metodologias ágeis |
| Communication | 204 | Reuniões, colaboração |
| Phrasal Verbs | 117 | Verbos frasais tech |
| Documentation/PRs | 102 | Git, docs, reviews |
| Tech Interviews | 518 | Entrevistas técnicas |
| **TOTAL** | **1535** | |

## 🚀 Próximos Passos

Depois de dominar esses cards:
1. Pratique em conversas reais (ex: reuniões em inglês)
2. Assista tech talks em inglês
3. Leia documentação técnica em inglês
4. Participe de comunidades internacionais (Discord, Slack)
5. Faça mock interviews em inglês

## 📝 Formato dos Cards

Cada card segue o formato:
- **Front**: Frase em inglês (como você usaria no trabalho)
- **Back**: Tradução em português

Exemplo:
- Front: "I'm implementing the new REST API endpoint."
- Back: "Estou implementando o novo endpoint da REST API."

## ✅ Checklist de Importação

- [ ] Importar 01_backend_development.csv
- [ ] Importar 02_devops_infrastructure.csv
- [ ] Importar 03_scrum_agile.csv
- [ ] Importar 04_communication_meetings.csv
- [ ] Importar 05_phrasal_verbs.csv
- [ ] Importar 06_documentation_prs.csv
- [ ] Importar 07_tech_interviews.csv
- [ ] Configurar TTS (opcional)
- [ ] Começar a estudar!

---

**Boa sorte nos estudos! 🎓**

Remember: Consistency is key. Study a little bit every day!
