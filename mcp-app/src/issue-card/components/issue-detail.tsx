import type { McpApp } from "@modelcontextprotocol/ext-apps";
import { marked } from "marked";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { t } from "../i18n";
import type { Issue } from "../types";
import { IssueTypeIcon } from "./issue-type-icon";
import { PriorityIcon } from "./priority-icon";
import { StatusBadge } from "./status-badge";
import { TransitionPicker } from "./transition-picker";

interface IssueDetailProps {
  issue: Issue;
  baseUrl: string;
  app?: McpApp;
  currentUser?: { name: string; displayName?: string };
  onRefresh?: () => void;
  children?: ReactNode;
}

function formatDate(iso: string): string {
  try {
    const locale = document.documentElement.lang || undefined;
    return new Date(iso).toLocaleDateString(locale, {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

function renderDescription(text: string): string {
  marked.setOptions({ breaks: true, gfm: true });
  return marked.parse(text) as string;
}

const LIVE_INTERVAL_MS = 30_000;

export function IssueDetail({
  issue,
  baseUrl,
  app,
  currentUser,
  onRefresh,
  children,
}: IssueDetailProps) {
  const issueUrl = `${baseUrl.replace(/\/$/, "")}/browse/${issue.key}`;
  const [assigning, setAssigning] = useState(false);
  const [live, setLive] = useState(false);
  const onRefreshRef = useRef(onRefresh);
  onRefreshRef.current = onRefresh;

  // Live polling: 30s cadence, paused when document is hidden.
  // No-op when `live` is false or no onRefresh callback exists.
  useEffect(() => {
    if (!live || !onRefreshRef.current) return;
    let timer: ReturnType<typeof setInterval> | null = null;
    const start = () => {
      if (timer) return;
      timer = setInterval(() => {
        if (document.visibilityState === "visible") {
          onRefreshRef.current?.();
        }
      }, LIVE_INTERVAL_MS);
    };
    const stop = () => {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    };
    const onVisibility = () => {
      if (document.visibilityState === "visible") start();
      else stop();
    };
    start();
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [live]);

  function handleOpenIssue() {
    if (app) app.openLink({ url: issueUrl });
  }

  async function handleAssignToMe() {
    if (!app || !currentUser) return;
    setAssigning(true);
    try {
      await app.callServerTool({
        name: "update_issue",
        arguments: {
          issue_key: issue.key,
          fields: JSON.stringify({ assignee: currentUser.name }),
        },
      });
      onRefresh?.();
    } catch {
      /* error handled silently */
    } finally {
      setAssigning(false);
    }
  }

  const isAssignedToMe = issue.assignee?.name === currentUser?.name;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0" }}>
      {/* Header: key + title */}
      <div style={{ marginBottom: "4px" }}>
        <span
          role="link"
          tabIndex={0}
          onClick={handleOpenIssue}
          onKeyDown={(e) => {
            if (e.key === "Enter") handleOpenIssue();
          }}
          style={{
            fontSize: "12px",
            color: "var(--accent)",
            cursor: app ? "pointer" : "default",
            textDecoration: "none",
          }}
        >
          {issue.key}
        </span>
      </div>
      <h3
        style={{
          fontSize: "15px",
          fontWeight: 600,
          lineHeight: 1.4,
          color: "var(--text)",
          margin: "0 0 8px 0",
        }}
      >
        {issue.summary}
      </h3>

      {/* Action bar: status dropdown + comment (like Jira's top bar) */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "8px",
          padding: "6px 0",
          borderBottom: "1px solid var(--border)",
          marginBottom: "10px",
          flexWrap: "wrap",
        }}
      >
        {app && onRefresh ? (
          <TransitionPicker
            app={app}
            issue={issue}
            onTransitioned={onRefresh}
          />
        ) : (
          <StatusBadge status={issue.status} />
        )}
        {children}
      </div>

      {/* Details + People grid */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: "12px",
          fontSize: "12px",
          marginBottom: "12px",
        }}
      >
        {/* Left: Details */}
        <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
          <div
            style={{
              fontWeight: 600,
              color: "var(--text)",
              marginBottom: "2px",
            }}
          >
            Details
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <span style={{ color: "var(--text-secondary)", minWidth: "55px" }}>
              Type:
            </span>
            <IssueTypeIcon name={issue.issue_type.name} size={14} />
            <span style={{ color: "var(--text)" }}>
              {issue.issue_type.name}
            </span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <span style={{ color: "var(--text-secondary)", minWidth: "55px" }}>
              Priority:
            </span>
            <PriorityIcon priority={issue.priority} />
            <span style={{ color: "var(--text)" }}>{issue.priority.name}</span>
          </div>
        </div>

        {/* Right: People */}
        <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
          <div
            style={{
              fontWeight: 600,
              color: "var(--text)",
              marginBottom: "2px",
            }}
          >
            People
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
            <div
              style={{ display: "flex", alignItems: "baseline", gap: "6px" }}
            >
              <span
                style={{ color: "var(--text-secondary)", minWidth: "65px" }}
              >
                {t("assignee")}:
              </span>
              <span
                style={{
                  color: issue.assignee
                    ? "var(--text)"
                    : "var(--text-secondary)",
                  fontStyle: issue.assignee ? "normal" : "italic",
                }}
              >
                {issue.assignee ? issue.assignee.displayName : t("unassigned")}
              </span>
            </div>
            {app && currentUser && !isAssignedToMe && (
              <span
                role="button"
                tabIndex={0}
                onClick={handleAssignToMe}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleAssignToMe();
                }}
                style={{
                  color: "var(--accent)",
                  cursor: "pointer",
                  fontSize: "11px",
                  marginLeft: "71px",
                }}
              >
                {assigning ? t("assigning") : t("assignToMe")}
              </span>
            )}
          </div>
          <div style={{ display: "flex", alignItems: "baseline", gap: "6px" }}>
            <span style={{ color: "var(--text-secondary)", minWidth: "65px" }}>
              {t("reporter")}:
            </span>
            <span style={{ color: "var(--text)" }}>
              {issue.reporter ? issue.reporter.displayName : "—"}
            </span>
          </div>
        </div>
      </div>

      {/* Dates + Live toggle */}
      <div
        style={{
          display: "flex",
          gap: "16px",
          fontSize: "11px",
          color: "var(--text-secondary)",
          marginBottom: "12px",
          alignItems: "center",
        }}
      >
        <span>Created: {formatDate(issue.created)}</span>
        <span>
          {t("updated")}: {formatDate(issue.updated)}
        </span>
        {onRefresh && (
          <span
            role="switch"
            aria-checked={live}
            tabIndex={0}
            onClick={() => setLive((v) => !v)}
            onKeyDown={(e) => {
              if (e.key === " " || e.key === "Enter") {
                e.preventDefault();
                setLive((v) => !v);
              }
            }}
            title={live ? t("liveTooltipOn") : t("liveTooltipOff")}
            style={{
              marginLeft: "auto",
              display: "inline-flex",
              alignItems: "center",
              gap: "4px",
              cursor: "pointer",
              color: live ? "var(--accent)" : "var(--text-secondary)",
              userSelect: "none",
            }}
          >
            <span
              style={{
                width: "6px",
                height: "6px",
                borderRadius: "50%",
                background: live ? "var(--accent)" : "var(--text-secondary)",
                opacity: live ? 1 : 0.5,
                animation: live
                  ? "mcp-live-pulse 1.6s ease-in-out infinite"
                  : "none",
              }}
            />
            {t("liveOn")}
          </span>
        )}
      </div>

      {/* Description (rendered Markdown) */}
      {issue.description && (
        <div style={{ marginBottom: "12px" }}>
          <div
            style={{
              fontWeight: 600,
              fontSize: "12px",
              color: "var(--text)",
              marginBottom: "4px",
            }}
          >
            Description
          </div>
          <div
            style={{
              fontSize: "13px",
              color: "var(--text)",
              maxHeight: "200px",
              overflowY: "auto",
              lineHeight: 1.6,
              wordBreak: "break-word",
            }}
            dangerouslySetInnerHTML={{
              __html: renderDescription(issue.description),
            }}
          />
        </div>
      )}

      {/* Comments */}
      {issue.comments.length > 0 && (
        <div>
          <div
            style={{
              fontWeight: 600,
              fontSize: "12px",
              color: "var(--text)",
              marginBottom: "6px",
            }}
          >
            {t("comments")} ({issue.comments.length})
          </div>
          {issue.comments.slice(0, 5).map((comment, i) => (
            <div
              key={i}
              style={{
                background: "var(--border)",
                borderRadius: "6px",
                padding: "8px",
                fontSize: "12px",
                marginBottom: "6px",
              }}
            >
              <div
                style={{
                  fontWeight: 600,
                  marginBottom: "3px",
                  color: "var(--text)",
                }}
              >
                {comment.author ? comment.author.displayName : "—"}
                <span
                  style={{
                    fontWeight: 400,
                    color: "var(--text-secondary)",
                    marginLeft: "8px",
                  }}
                >
                  {formatDate(comment.created)}
                </span>
              </div>
              <div
                style={{ color: "var(--text)", lineHeight: 1.5 }}
                dangerouslySetInnerHTML={{
                  __html: renderDescription(comment.body),
                }}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
