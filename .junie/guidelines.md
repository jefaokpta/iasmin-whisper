# Iasmin-whisper-transcriptor

Backend responsável por consumir mensagens de CDR via Kafka, realizar transcrição de áudios com Whisper (instalado no host Linux) e enviar os segmentos para o iasmin-backend.

Criar README completo suficiente para que um agente (humano ou IA) possa:

entender a arquitetura e o fluxo de dados;
instalar e rodar o projeto localmente;
configurar variáveis de ambiente e dependências externas (Kafka, Whisper, ffmpeg);
debugar problemas e adicionar novas features seguindo boas práticas (DRY e Immutability)

## Visão Geral
O serviço conecta-se a um cluster Kafka, consome eventos de CDR no tópico transcriptions:
1. Verifica no iasmin-backend se já existe transcrição para aquele uniqueId.
2. Se não existir, baixa o(s) arquivo(s) de áudio do PABX (IASMIN_PABX_URL) para a pasta local audios/.
3. Executa o CLI whisper no host para transcrever o áudio, gerando um JSON em transcriptions/.
4. Envia os segmentos gerados via HTTP para o iasmin-backend.

# exemplo do comando whisper
whisper audio.sln --model=turbo --language=pt --fp16=False --beam_size=5 --patience=2 --output_format=json

# exemplo da CDR
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
