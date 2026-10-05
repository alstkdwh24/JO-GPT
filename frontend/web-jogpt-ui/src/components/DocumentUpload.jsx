import { useState } from "react";
import CONFIG from "../config/config";
import { fetchWithRefresh } from "../config/tokenRefresh";

export default function DocumentUpload() {
    const [mode, setMode] = useState("text");
    const [context, setContext] = useState("");
    const [source, setSource] = useState("");
    const [category, setCategory] = useState("");
    const [url, setUrl] = useState("");
    const [file, setFile] = useState(null);
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState("");

    const handleSave = async () => {
        if (loading) return;
        setLoading(true);
        setMessage("");
        try{
            let finalContext = context;

            //URL 모드면 서버에서 크롤링
            if (mode === "url") {
                const res =await fetchWithRefresh(
                    `${CONFIG.API_BASE_URL}/contents/crawl`,
                    {
                        method: "POST",
                        credentials: "include",
                        headers: {"Content-Type": "application/json"},
                        body: JSON.stringify({
                            url: url
                        })
                    }
                );
                finalContext = await res.text();
            }
            if(mode === "file"){
                finalContext = await readFile(file);
            }
            // 저장 API 호출
            const res = await fetchWithRefresh(
                `${CONFIG.API_CONTENTS_URL}/contents/saveDocument`,
                {
                    method: "POST",
                    credentials: "include",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        context: finalContext,
                        source: source || (mode === "url" ? url : file?.name) || "직접입력",
                        category: category || "일반"
                    })
                }
            );
            if(res.ok){
                setMessage("저장 완료");
                setContext("");
                setSource("");
                setCategory("");
                setUrl("");
                setFile(null);
            }else{
                setMessage("❌ 저장 실패!");
            }
        } catch (e) {
            setMessage(" 오류 발생:" + e.message);
        } finally {
            setLoading(false);
        }
    }

    // 파일 읽기
    const readFile = (file) => new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsText(file, "UTF-8");
    });
    return (
        <div className="document-upload">
            <h3>📚 문서 저장 (RAG)</h3>

            {/* 모드 선택 */}
            <div className="mode-select">
                {["text", "file", "url"].map((m) => (
                    <button
                        key={m}
                        className={mode === m ? "active" : ""}
                        onClick={() => setMode(m)}
                    >
                        {m === "text" ? "✍️ 텍스트" : m === "file" ? "📄 파일" : "🔗 URL"}
                    </button>
                ))}
            </div>

            {/* 텍스트 입력 */}
            {mode === "text" && (
                <textarea
                    placeholder="저장할 내용을 입력하세요..."
                    value={context}
                    onChange={(e) => setContext(e.target.value)}
                    rows={6}
                />
            )}

            {/* 파일 업로드 */}
            {mode === "file" && (
                <input
                    type="file"
                    accept=".txt,.pdf,.md"
                    onChange={(e) => setFile(e.target.files[0])}
                />
            )}

            {/* URL 입력 */}
            {mode === "url" && (
                <input
                    type="text"
                    placeholder="https://..."
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                />
            )}

            {/* 출처 & 카테고리 */}
            <input
                type="text"
                placeholder="출처 (예: 공식문서, 블로그)"
                value={source}
                onChange={(e) => setSource(e.target.value)}
            />
            <input
                type="text"
                placeholder="카테고리 (예: 맛집, 기술, 일반)"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
            />

            <button onClick={handleSave} disabled={loading}>
                {loading ? "저장 중..." : "저장하기"}
            </button>

            {message && <p>{message}</p>}
        </div>
    )

}