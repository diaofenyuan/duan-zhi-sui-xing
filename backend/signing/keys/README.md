*.seed 为 dev 签名私钥（已 gitignore），仅用于重签 backend/fixtures 下的 dev fixture。
重新生成流程：删除本目录 *.pub 后运行 backend/fixtures/generate.ps1，再把新公钥十六进制写入 app TrustedKeys.java。
生产密钥必须离线生成并托管，严禁使用 dev 密钥。
