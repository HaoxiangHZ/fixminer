package edu.lu.uni.serval.violation.parse;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;

import edu.lu.uni.serval.git.exception.GitRepositoryNotFoundException;
import edu.lu.uni.serval.git.exception.NotValidGitRepositoryException;
import edu.lu.uni.serval.git.travel.GitRepository;
import edu.lu.uni.serval.utils.FileHelper;
import edu.lu.uni.serval.violation.Alarm;
import edu.lu.uni.serval.violation.Violation;

/**
 * 
 * @author kui.liu
 *
 */
public class ViolationParser {
	
	public void parseViolations(String fixedAlarmFile, List<File> repos, String previousFilesPath, String revisedFilesPath, String positionsFilePath, String diffentryFilePath) {
		AlarmsReader reader = new AlarmsReader();
		Map<String, Violation> violations = reader.readAlarmsList(fixedAlarmFile);
		int a = 0;
		for (Map.Entry<String , Violation> entry : violations.entrySet()) {
			String projectName = entry.getKey();
			String repoName = "";
			for (File repo : repos) {
				if (repo.getName().equals(projectName)) {
					repoName = repo.getPath() + "/";
					break;
				}
			}
			if ("".equals(repoName)) {
				a ++;
				System.out.println(projectName);
				continue;
			}
			Violation violation = entry.getValue();
			List<Alarm> alarms = violation.getAlarms();
			
			String repoPath = repoName + "/.git";
			GitRepository gitRepo = new GitRepository(repoPath, revisedFilesPath, previousFilesPath);
			try {
				gitRepo.open();
				for (Alarm alarm : alarms) {
					String buggyCommitId = alarm.getBuggyCommitId();
					String buggyFileName = alarm.getBuggyFileName();
					String buggyFileContent = gitRepo.getFileContentByCommitIdAndFileName(buggyCommitId, buggyFileName);
					if (buggyFileContent == null || "".equals(buggyFileContent)) continue;
					
					String fixedCommitId = alarm.getFixedCommitId();
					String fixedFileName = alarm.getFixedFileName();
					String fixedFileContent = gitRepo.getFileContentByCommitIdAndFileName(fixedCommitId, fixedFileName);
					if (fixedFileContent == null || "".equals(fixedFileContent)) continue;
					
					String diffentry = gitRepo.getDiffentryByTwoCommitIds(buggyCommitId, fixedCommitId, fixedFileName);
					if (diffentry == null) continue;
					
					String commitId = buggyCommitId.substring(0, 6) + "_" + fixedCommitId.substring(0, 6);
					String fileName = fixedFileName.replaceAll("/", "#");
					fileName = projectName + "_" + commitId + fileName;
					if (fileName.length() > 240) {
						List<File> files = FileHelper.getAllFilesInCurrentDiectory(revisedFilesPath, ".java");
						fileName = files.size() + "TooLongFileName.java";
					}
					String buggyFile = previousFilesPath + "prev_" + fileName;
					String fixedFile = revisedFilesPath + fileName;
					fileName = fileName.replace(".java", ".txt");
					String positionFile = positionsFilePath + fileName;
					String diffentryFile = diffentryFilePath + fileName;
					FileHelper.outputToFile(buggyFile, buggyFileContent, false);
					FileHelper.outputToFile(fixedFile, fixedFileContent, false);
					FileHelper.outputToFile(positionFile, readPosition(alarm.getPositions()), false);
					FileHelper.outputToFile(diffentryFile, diffentry, false);
				}
			} catch (GitRepositoryNotFoundException e) {
				e.printStackTrace();
			} catch (NotValidGitRepositoryException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (RevisionSyntaxException e) {
				e.printStackTrace();
			} catch (GitAPIException e) {
				e.printStackTrace();
			} finally {
				gitRepo.close();
			}
		}
		System.out.println(a);
	}

	private String readPosition(Map<Integer, Integer> positions) {
		String positionsStr = "";
		for (Map.Entry<Integer, Integer> entry : positions.entrySet()) {
			positionsStr += entry.getKey() + ":" + entry.getValue() + "\n";
		}
		return positionsStr;
	}
}
