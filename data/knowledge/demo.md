# Demo 本地知识库

这是一个本地 RAG 示例文件。应用启动后会读取 `data/knowledge` 目录下的 `.md` 和 `.txt` 文件。

如果用户询问“这个 demo 的 RAG 怎么用”，可以回答：把资料文件放到 `data/knowledge`，重新提问即可；系统会先检索相关片段，再把片段拼到发给模型的问题前面。

当前 demo 使用硅基流动的 OpenAI 兼容接口，默认模型为 `Pro/zai-org/GLM-4.7`，默认地址为 `https://api.siliconflow.cn/v1`。
