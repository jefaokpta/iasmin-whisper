# Iasmin-whisper-transcriptor

Backend responsável por consumir mensagens de CDR via Kafka, realizar transcrição de áudios com Whisper (instalado no host Linux) e enviar os segmentos para o iasmin-backend.

Este README foi escrito para permitir que qualquer agente (humano ou IA) consiga:
- Entender a arquitetura e o fluxo de dados
- Instalar e rodar o projeto localmente
- Configurar variáveis de ambiente e dependências externas (Kafka, Whisper, ffmpeg)
- Debugar problemas e adicionar novas features seguindo boas práticas (DRY e Immutability)


## Sumário
- Visão Geral
- Arquitetura e Fluxo de Dados
- Pré‑requisitos
- Instalação e Configuração
- Como Rodar Localmente
- Dependências Externas (Kafka, Whisper, ffmpeg)
- Simular um CDR (teste end‑to‑end)
- Estrutura do Projeto
- Logs e Debug
- Troubleshooting (erros comuns)
- Boas Práticas para Contribuição (DRY e Immutability)


## Visão Geral
O serviço conecta-se a um cluster Kafka, consome eventos de CDR no tópico `transcriptions` e para cada evento:
1. Verifica no iasmin-backend se já existe transcrição para aquele `uniqueId`.
2. Se não existir, baixa o(s) arquivo(s) de áudio do PABX (`IASMIN_PABX_URL`) para a pasta local `audios/`.
3. Executa o CLI `whisper` no host para transcrever o áudio, gerando um JSON em `transcriptions/`.
4. Envia os segmentos gerados via HTTP para o iasmin-backend.

Exemplo de comando whisper (utilizado pelo serviço):
```
whisper audio.sln --model=turbo --language=pt --fp16=False --beam_size=5 --patience=2 --output_format=json
```

Exemplo de payload CDR (mensagem no Kafka):
```
{
  "peer": "21",
  "src": "1154201020",
  "destination": "1935002560",
  "callerId": "\"Rogerio Libreti\" <1154201020>",
  "duration": 11,
  "billableSeconds": 7,
  "uniqueId": "1757620718.2770",
  "disposition": "ANSWERED",
  "company": "100023",
  "startTime": "2025-09-11 16:58:38",
  "callRecord": "1757620718-2770.mp3",
  "userfield": "OUTBOUND",
  "title": null,
  "summary": null,
  "sentiment": null,
  "temperature": null,
  "engagement": null,
  "mostFrequentWords": null,
  "action": null,
  "id": 2934,
  "status": "ANALYZING"
}
```


## Arquitetura e Fluxo de Dados
- Entrada: mensagens CDR no Kafka (tópico `transcriptions`).
- Orquestração: aplicação Spring Boot (Kotlin) lê a CDR, checa no backend, baixa áudio, chama Whisper, envia resultados.
- Processamento:
  - Download do áudio do PABX para `audios/` (cache local temporário).
  - Execução do binário/CLI `whisper` para gerar `transcriptions/<uniqueId>.json`.
  - Leitura do JSON de segmentos e envio para o iasmin-backend via HTTP.
- Saída: segmentos de transcrição persistidos no iasmin-backend.

Observações:
- O projeto usa Java 21 e Spring Boot 3.5.
- Threads virtuais estão habilitadas por padrão (ver `application.yml`).


## Pré‑requisitos
- Java 21 (JDK 21)
- Kotlin 1.9+
- Gradle (wrapper incluso)
- Kafka em execução (local ou remoto)
- Whisper CLI instalado no host (via Python/pip) e `ffmpeg` disponível no PATH
- Acesso ao PABX (HTTP estático) e ao iasmin-backend


## Instalação e Configuração
1) Clone o repositório
- git clone <repo>
- cd iasmin-whisper-transcriptor

2) Configure variáveis de ambiente
Você pode usar um arquivo `.env` na raiz (já existe um exemplo) ou exportar manualmente.

Variáveis principais (com defaults do `application.yml`):
- VEIA_KAFKA_BROKER: broker do Kafka. Default: `localhost:9092`
- IASMIN_PABX_URL: base URL para baixar os áudios. Default: `http://localhost:8080`
- IASMIN_BACKEND_URL: base URL do iasmin-backend. Default: `http://localhost:8081`
- WHISPER_COMMAND: caminho do executável `whisper`. Default: `whisper`

Exemplo de `.env` (já presente):
```
WHISPER_COMMAND=/opt/whisper-api/venv/bin/whisper
IASMIN_PABX_URL=https://iasmin-pabx.vipsolutions.com.br/static
IASMIN_BACKEND_URL=https://iasmin-5bffa268e5ef.herokuapp.com
VEIA_KAFKA_BROKER=veia.vipsolutions.com.br:9094
```

Para shells compatíveis, você pode carregar o `.env` antes de rodar:
- export $(grep -v '^#' .env | xargs)

3) Crie as pastas de trabalho locais (se necessário)
- mkdir -p audios transcriptions


## Como Rodar Localmente
Opção A) Usando o Gradle Wrapper
- ./gradlew bootRun

Opção B) Gerando o JAR
- ./gradlew clean bootJar
- java -jar build/libs/iasmin-whisper-transcriptor-0.0.1-SNAPSHOT.jar

Testes
- ./gradlew test


## Dependências Externas
### Kafka
Você pode rodar um Kafka local via Docker Compose (exemplo de snippet):
```
version: '3.8'
services:
  zookeeper:
    image: bitnami/zookeeper:3.9
    environment:
      - ALLOW_ANONYMOUS_LOGIN=yes
    ports:
      - "2181:2181"
  kafka:
    image: bitnami/kafka:3.7
    environment:
      - KAFKA_BROKER_ID=1
      - KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181
      - ALLOW_PLAINTEXT_LISTENER=yes
      - KAFKA_LISTENERS=PLAINTEXT://:9092
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper
```
Crie o tópico (se precisar):
- docker exec -it <kafka-container> kafka-topics.sh --create --topic transcriptions --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1

### Whisper CLI
Instalação (exemplo com Python/venv):
- python3 -m venv ~/whisper-venv
- source ~/whisper-venv/bin/activate
- pip install -U openai-whisper
- which whisper  # copie o caminho para WHISPER_COMMAND

Observação: o Whisper usa `ffmpeg`. Garanta que está instalado e visível no PATH.

### ffmpeg
Linux (Debian/Ubuntu):
- sudo apt-get update && sudo apt-get install -y ffmpeg

macOS (Homebrew):
- brew install ffmpeg

Teste rápido:
- ffmpeg -version
- whisper --help


## Simular um CDR (teste end‑to‑end)
Com o Kafka rodando e a aplicação em execução, publique uma CDR no tópico `transcriptions`:

Usando o Kafka console producer (no container):
- docker exec -it <kafka-container> bash
- kafka-console-producer.sh --broker-list localhost:9092 --topic transcriptions
  - Cole o JSON do exemplo de CDR e pressione Enter.

A aplicação deverá:
- Logar o recebimento da CDR.
- Baixar o arquivo de áudio `callRecord` a partir de `IASMIN_PABX_URL` para `audios/`.
- Rodar o comando `whisper` e gerar `transcriptions/<uniqueId>.json`.
- Enviar os segmentos para o `IASMIN_BACKEND_URL`.


## Estrutura do Projeto
- build.gradle.kts / settings.gradle.kts: Build e metadados.
- src/main/kotlin: código da aplicação (Spring Boot 3 + Kotlin).
- src/main/resources/application.yml: configurações (inclui variáveis de ambiente mencionadas acima).
- .env: exemplo de configuração de ambiente.
- audios/: diretório local para armazenar downloads temporários de áudios. (crie se não existir)
- transcriptions/: diretório local para armazenar JSON das transcrições. (crie se não existir)


## Logs e Debug
- Níveis de log: por padrão, logs informativos. Para depuração detalhada, rode com `--debug` ou configure `logging.level` via propriedades Spring.
- Verificar variáveis carregadas:
  - Em tempo de execução, confira os valores efetivos de `VEIA_KAFKA_BROKER`, `IASMIN_PABX_URL`, `IASMIN_BACKEND_URL`, `WHISPER_COMMAND` nos logs de inicialização.
- Testar o Whisper manualmente:
  - whisper audios/arquivo.wav --model=turbo --language=pt --fp16=False --beam_size=5 --patience=2 --output_format=json
  - Confirme que gera um JSON em `transcriptions/` (ou o diretório apontado pela sua execução manual).
- Testar conectividade Kafka:
  - Telnet/Netcat para a porta do broker; ou use `kafka-topics.sh --bootstrap-server <host:porta> --list`.
- Verificar permissões de escrita nas pastas `audios/` e `transcriptions/`.


## Troubleshooting (erros comuns)
- `whisper: command not found`
  - Ajuste a variável `WHISPER_COMMAND` para o caminho correto do executável; verifique se o venv está ativado.
- `ffmpeg not found`
  - Instale o `ffmpeg` e certifique-se de que está no PATH.
- `Connection refused` ao acessar Kafka
  - Verifique `VEIA_KAFKA_BROKER` e se o Kafka está escutando no endereço/porta anunciados.
- Falha ao baixar áudio do PABX
  - Cheque `IASMIN_PABX_URL` e se o arquivo `callRecord` existe publicamente.
- Erros de HTTP ao enviar para o backend
  - Confirme `IASMIN_BACKEND_URL`, autenticação (se necessário) e formato do payload esperado.


## Boas Práticas para Contribuição (DRY e Immutability)
- Prefira `val` a `var` e use `data class` imutáveis para representar CDRs e segmentos de transcrição.
- Separe responsabilidades: Consumer Kafka, Serviço de Download, Serviço de Transcrição (processo externo), Cliente HTTP do Backend.
- DRY: extraia lógica repetida para funções/utilitários; evite duplicação em downloads, comandos e parse de JSON.
- Efeitos colaterais controlados: encapsule chamadas a processos externos (whisper) e IO em serviços com fronteiras claras.
- Tratamento de erros: use Result/Either ou exceções específicas; registre erros com contexto (uniqueId, arquivo, tempos).
- Tempo de execução/Timeouts: ao chamar processos externos e HTTP, defina timeouts e faça retries exponenciais quando apropriado.
- Testes: crie testes unitários para parsing de CDR, construção de comandos whisper e montagem de requisições HTTP; use `spring-kafka-test` para testes de integração.


## Referências
- Spring Boot + Kafka: https://docs.spring.io/spring-boot/reference/messaging/kafka.html
- Whisper (openai-whisper): https://github.com/openai/whisper
- ffmpeg: https://ffmpeg.org/
